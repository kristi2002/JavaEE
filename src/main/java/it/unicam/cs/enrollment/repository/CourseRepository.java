package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.common.PageRequest;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Semester;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Course}.
 *
 * <p>The interesting method here is {@link #findByIdWithPessimisticLock}, which
 * is what makes the capacity rule actually correct under concurrency.
 */
@ApplicationScoped
public class CourseRepository extends AbstractJpaRepository<Course> {

    public CourseRepository() {
        super(Course.class);
    }

    public Optional<Course> findByCodeAndYear(String code, int academicYear) {
        TypedQuery<Course> query = em()
                .createNamedQuery(Course.FIND_BY_CODE_AND_YEAR, Course.class)
                .setParameter("code", code)
                .setParameter("academicYear", academicYear);
        return singleResult(query);
    }

    /** Courses whose enrollment window contains {@code now}. */
    public List<Course> findOpenForEnrollment(Instant now) {
        return em().createNamedQuery(Course.FIND_OPEN_FOR_ENROLLMENT, Course.class)
                .setParameter("now", now)
                .getResultList();
    }

    /**
     * Loads a course with everything the detail view needs: its professor AND
     * its prerequisites, in one query.
     *
     * <p>That exception is the single most-encountered JPA error, and the cure is
     * always the same: decide UP FRONT what the use case needs, and fetch it in
     * the query. Do not "fix" it by making the association EAGER, and do not fix
     * it by keeping the transaction open while rendering the response (the
     * "Open Session In View" anti-pattern).
     *
     * <h3>How this method got its second fetch join</h3>
     * It originally fetched only {@code prerequisites}, because that is what the
     * enrollment rule needs. But the REST layer maps the same entity to a
     * response, and {@code CourseMapper} reads {@code course.getProfessor()} -
     * so {@code GET /courses/{id}} failed in the running server with
     * {@code LazyInitializationException: could not initialize proxy [Professor#1]}.
     *
     * <p>The lesson is the one that makes lazy loading hard in practice: a fetch
     * plan is a contract between the query and EVERY consumer of its result. Add
     * a consumer that touches one more association and the query must change
     * with it. That is why each mapper in this project documents its
     * preconditions, and why {@code CourseRepositoryIT} clears the persistence
     * context before asserting - it reproduces the detached conditions the REST
     * layer actually maps under.
     *
     * <p>{@code JOIN FETCH} for the professor (mandatory, so an inner join is
     * correct) and {@code LEFT JOIN FETCH} for the prerequisites (a course
     * usually has none, and an inner join would return no row at all).
     */
    public Optional<Course> findByIdWithPrerequisites(Long id) {
        TypedQuery<Course> query = em().createQuery(
                        "SELECT DISTINCT c FROM Course c "
                                + "JOIN FETCH c.professor "
                                + "LEFT JOIN FETCH c.prerequisites "
                                + "WHERE c.id = :id",
                        Course.class)
                .setParameter("id", id);
        return singleResult(query);
    }

    /**
     * Loads the course FOR UPDATE, so the capacity check is race-free.
     *
     * <h3>Why this is necessary</h3>
     * The last seat of a course is a CONTENDED RESOURCE. Consider two students
     * enrolling at the same instant with 1 seat left:
     * <pre>
     *   T1: count seats -> 29 of 30      T2: count seats -> 29 of 30
     *   T1: 29 &lt; 30, ok                  T2: 29 &lt; 30, ok
     *   T1: INSERT enrollment            T2: INSERT enrollment
     *   COMMIT                           COMMIT        -&gt; 31 students. Bug.
     * </pre>
     * Optimistic locking does not help: neither transaction modified the course
     * row, so no version changed. The unique constraint does not help either -
     * these are two different students.
     *
     * <p>Taking a pessimistic lock on the COURSE row makes the second
     * transaction wait until the first commits, so it sees the true count. The
     * course row is used as the serialisation point for its own capacity - a
     * standard technique worth recognising.
     */
    public Optional<Course> findByIdWithPessimisticLock(Long id) {
        return findByIdForUpdate(id);
    }

    /** Catalogue browsing, filtered by academic year and optionally by semester. */
    public Page<Course> findByYearAndSemester(int academicYear,
                                              Semester semester,
                                              PageRequest pageRequest) {
        // Two fixed shapes rather than a Criteria build: with a single optional
        // filter, a small branch is more readable than the Criteria ceremony.
        // Reach for Criteria when the number of combinations stops being trivial.
        String jpql = "SELECT c FROM Course c JOIN FETCH c.professor "
                + "WHERE c.academicYear = :academicYear "
                + (semester != null ? "AND c.semester = :semester " : "")
                + "ORDER BY c.code ASC";

        TypedQuery<Course> query = em().createQuery(jpql, Course.class)
                .setParameter("academicYear", academicYear);
        if (semester != null) {
            query.setParameter("semester", semester);
        }

        List<Course> content = query
                .setFirstResult(pageRequest.getOffset())
                .setMaxResults(pageRequest.getPageSize())
                .getResultList();

        String countJpql = "SELECT COUNT(c) FROM Course c "
                + "WHERE c.academicYear = :academicYear "
                + (semester != null ? "AND c.semester = :semester" : "");

        TypedQuery<Long> countQuery = em().createQuery(countJpql, Long.class)
                .setParameter("academicYear", academicYear);
        if (semester != null) {
            countQuery.setParameter("semester", semester);
        }

        return Page.of(content, pageRequest, countQuery.getSingleResult());
    }

    public boolean existsByCodeAndYear(String code, int academicYear) {
        Long count = em().createQuery(
                        "SELECT COUNT(c) FROM Course c "
                                + "WHERE c.code = :code AND c.academicYear = :academicYear",
                        Long.class)
                .setParameter("code", code)
                .setParameter("academicYear", academicYear)
                .getSingleResult();
        return count > 0;
    }
}
