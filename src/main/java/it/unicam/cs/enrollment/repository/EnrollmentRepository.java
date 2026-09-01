package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.domain.model.Enrollment;
import it.unicam.cs.enrollment.domain.model.EnrollmentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data access for {@link Enrollment}, the association entity.
 *
 * <p>Notice that most methods take IDs rather than entities. A repository that
 * demands a fully loaded {@code Student} just to count rows forces the caller to
 * fetch data it does not need.
 */
@ApplicationScoped
public class EnrollmentRepository extends AbstractJpaRepository<Enrollment> {

    /**
     * The statuses that consume a seat. Kept here as a constant so the capacity
     * query and {@link EnrollmentStatus#occupiesSeat()} state one rule in one
     * place instead of two that can disagree.
     */
    private static final Set<EnrollmentStatus> SEAT_OCCUPYING_STATUSES =
            EnumSet.copyOf(Arrays.stream(EnrollmentStatus.values())
                    .filter(EnrollmentStatus::occupiesSeat)
                    .collect(Collectors.toList()));

    public EnrollmentRepository() {
        super(Enrollment.class);
    }

    /**
     * Has this student already enrolled in this course?
     *
     * <p>Used for the friendly duplicate check before insertion. The hard
     * guarantee still comes from the UNIQUE constraint on
     * {@code (student_id, course_id)} - see {@link Enrollment}.
     */
    public Optional<Enrollment> findByStudentAndCourse(Long studentId, Long courseId) {
        TypedQuery<Enrollment> query = em()
                .createNamedQuery(Enrollment.FIND_BY_STUDENT_AND_COURSE, Enrollment.class)
                .setParameter("studentId", studentId)
                .setParameter("courseId", courseId);
        return singleResult(query);
    }

    /**
     * Counts seats currently taken on a course.
     *
     * <p>An AGGREGATE QUERY, not a collection walk. The alternative -
     * {@code course.getEnrollments().size()} - loads every enrollment row into
     * memory to produce one number. On a 300-student course that is 300 objects
     * built and immediately discarded. Let the database do arithmetic; it is
     * extremely good at it.
     */
    public long countOccupiedSeats(Long courseId) {
        return em().createNamedQuery(Enrollment.COUNT_OCCUPIED_SEATS, Long.class)
                .setParameter("courseId", courseId)
                .setParameter("occupyingStatuses", SEAT_OCCUPYING_STATUSES)
                .getSingleResult();
    }

    /**
     * A student's full transcript, with student, courses and professors fetched
     * eagerly in the same query.
     */
    public List<Enrollment> findByStudentWithCourse(Long studentId) {
        return em().createNamedQuery(Enrollment.FIND_BY_STUDENT_WITH_COURSE, Enrollment.class)
                .setParameter("studentId", studentId)
                .getResultList();
    }

    /**
     * Loads one enrollment with every association the API response needs.
     *
     * <p>Used instead of the plain {@code findById} throughout the service,
     * because the REST layer maps the entity to a DTO AFTER the transaction has
     * committed. At that point the persistence context is gone and any
     * untouched lazy association throws {@code LazyInitializationException}.
     *
     * <p>Fetching what the use case needs while the transaction is still open is
     * the correct fix. The two tempting alternatives are both traps: making
     * associations EAGER punishes every other query, and keeping the transaction
     * open across rendering (the "Open Session In View" pattern) hides N+1
     * problems and holds database resources during I/O.
     */
    public Optional<Enrollment> findByIdWithDetails(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        TypedQuery<Enrollment> query = em()
                .createNamedQuery(Enrollment.FIND_BY_ID_WITH_DETAILS, Enrollment.class)
                .setParameter("id", id);
        return singleResult(query);
    }

    /**
     * Counts occupied seats for MANY courses in a single {@code GROUP BY} query.
     *
     * <h3>Why this method exists</h3>
     * The course list endpoint shows "seats available" for each of 20 courses.
     * Calling {@link #countOccupiedSeats(Long)} in a loop would issue 20
     * queries - the N+1 PROBLEM, arriving through the back door in the mapping
     * layer rather than through a lazy association.
     *
     * <p>The fix is the same one you apply everywhere: turn N queries into one
     * that takes a collection of ids, then look results up in a {@link Map}.
     * Recognising this shape - "I am about to query inside a loop" - is one of
     * the most valuable performance instincts to build.
     *
     * <p>Note the {@code Object[]} result: a JPQL query selecting several
     * expressions returns an array per row. The type-safe alternative is a
     * CONSTRUCTOR EXPRESSION, {@code SELECT new com.example.SeatCount(...)},
     * which is nicer when the projection is reused.
     *
     * @return course id to occupied-seat count. Courses with zero enrollments do
     *         NOT appear (GROUP BY produces no row for them), so callers must
     *         use {@code getOrDefault(id, 0L)}.
     */
    public Map<Long, Long> countOccupiedSeatsByCourse(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            // Guard clause: an empty IN list is a syntax error on several
            // databases, and skipping the round trip is free.
            return Collections.emptyMap();
        }

        List<Object[]> rows = em().createQuery(
                        "SELECT e.course.id, COUNT(e) FROM Enrollment e "
                                + "WHERE e.course.id IN :courseIds "
                                + "AND e.status IN :occupyingStatuses "
                                + "GROUP BY e.course.id",
                        Object[].class)
                .setParameter("courseIds", courseIds)
                .setParameter("occupyingStatuses", SEAT_OCCUPYING_STATUSES)
                .getResultList();

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * Prerequisite check: has this student PASSED the course with the given code
     * (in any academic year)?
     *
     * <p>Matching on the code rather than the course id is deliberate. A
     * prerequisite is "you must have passed Programming 1", not "you must have
     * passed the 2024 instance of Programming 1". Modelling that correctly
     * matters more than the query itself.
     */
    public boolean hasCompletedCourseCode(Long studentId, String courseCode) {
        Long count = em().createNamedQuery(Enrollment.HAS_COMPLETED_COURSE_CODE, Long.class)
                .setParameter("studentId", studentId)
                .setParameter("courseCode", courseCode)
                .getSingleResult();
        return count > 0;
    }

    /** All enrollments on a course with a given status, ordered by student surname. */
    public List<Enrollment> findByCourseAndStatus(Long courseId, EnrollmentStatus status) {
        return em().createQuery(
                        "SELECT e FROM Enrollment e "
                                + "JOIN FETCH e.student s "
                                + "JOIN FETCH e.course c "
                                + "JOIN FETCH c.professor "
                                + "WHERE e.course.id = :courseId AND e.status = :status "
                                + "ORDER BY s.lastName ASC, s.firstName ASC",
                        Enrollment.class)
                .setParameter("courseId", courseId)
                .setParameter("status", status)
                .getResultList();
    }

    /**
     * A BULK UPDATE: closes stale ACTIVE enrollments for courses whose academic
     * year has ended.
     *
     * <h3>Bulk operations bypass the persistence context - know the consequences</h3>
     * {@code executeUpdate()} issues one {@code UPDATE ... WHERE ...} statement.
     * That is enormously faster than loading 10,000 entities and mutating each.
     * But:
     * <ul>
     *   <li>Entity lifecycle callbacks ({@code @PreUpdate}) do NOT run, so
     *       {@code updated_at} must be set by hand in the statement.</li>
     *   <li>The {@code @Version} column is NOT incremented, so an entity another
     *       transaction is holding will not notice the change. Hibernate offers
     *       the HQL extension {@code UPDATE VERSIONED Enrollment e SET ...} to
     *       bump it; we stay on portable JPQL here and accept the limitation,
     *       which is safe because this job runs when nothing else is writing.</li>
     *   <li>Entities already loaded in the persistence context become STALE.
     *       Anything holding one keeps the old values.</li>
     * </ul>
     * The rule: use bulk operations from a short, dedicated transaction (like our
     * scheduled job) and not in the middle of a request that also manipulates the
     * same entities.
     *
     * @return the number of rows affected
     */
    public int closeStaleEnrollments(int academicYearBefore) {
        return em().createQuery(
                        "UPDATE Enrollment e "
                                + "SET e.status = :withdrawn, "
                                + "    e.updatedAt = :now "
                                + "WHERE e.status = :active "
                                + "AND e.course.academicYear < :year")
                .setParameter("withdrawn", EnrollmentStatus.WITHDRAWN)
                .setParameter("active", EnrollmentStatus.ACTIVE)
                .setParameter("now", java.time.Instant.now())
                .setParameter("year", academicYearBefore)
                .executeUpdate();
    }
}
