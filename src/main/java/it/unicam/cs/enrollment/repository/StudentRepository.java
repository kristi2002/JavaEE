package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.common.PageRequest;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.domain.model.StudentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Data access for {@link Student}.
 *
 * <h2>{@code @ApplicationScoped}: one instance for the whole application</h2>
 * The bean holds no mutable state - only the injected EntityManager proxy - so a
 * single shared instance is safe and avoids per-request allocation. This is the
 * correct default scope for stateless services and repositories.
 *
 * <p>Historically this would have been an {@code @Stateless} EJB. CDI beans have
 * largely replaced session beans for this role: they are lighter, and since
 * {@code @Transactional} (from JTA 1.2) works on any CDI bean, the last real
 * reason to use an EJB here disappeared.
 */
@ApplicationScoped
public class StudentRepository extends AbstractJpaRepository<Student> {

    /**
     * CDI requires a no-argument constructor to create its client proxy for a
     * normal-scoped bean. It calls {@code super} with the entity class, which is
     * how {@link AbstractJpaRepository} works around generic type erasure.
     */
    public StudentRepository() {
        super(Student.class);
    }

    /**
     * Lookup by natural key, using the {@code @NamedQuery} declared on the
     * entity.
     *
     * <p>Named queries are parsed and validated when the persistence unit is
     * built, i.e. at DEPLOY time. A typo in the JPQL fails the deployment
     * instead of surfacing on a user request at 2am. Prefer them for every fixed
     * query.
     *
     * <p>{@code setParameter} produces a JDBC BIND PARAMETER. This is not a
     * stylistic choice: string-concatenating a value into a query is what SQL
     * injection is. Bound parameters also let the database reuse its execution
     * plan across calls.
     */
    public Optional<Student> findByStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.isEmpty()) {
            return Optional.empty();
        }
        TypedQuery<Student> query = em()
                .createNamedQuery(Student.FIND_BY_STUDENT_NUMBER, Student.class)
                .setParameter("studentNumber", studentNumber);
        return singleResult(query);
    }

    /**
     * Existence check.
     *
     * <p>Note it counts instead of loading the entity. Fetching a whole row plus
     * its columns to then throw everything away and keep a boolean is wasteful;
     * {@code COUNT} lets the database answer from an index without touching the
     * table.
     */
    public boolean existsByStudentNumber(String studentNumber) {
        Long count = em().createQuery(
                        "SELECT COUNT(s) FROM Student s WHERE s.studentNumber = :studentNumber",
                        Long.class)
                .setParameter("studentNumber", studentNumber)
                .getSingleResult();
        return count > 0;
    }

    public boolean existsByEmail(String email) {
        Long count = em().createQuery(
                        "SELECT COUNT(s) FROM Student s WHERE s.email.value = :email",
                        Long.class)
                .setParameter("email", email == null ? null : email.toLowerCase(Locale.ROOT))
                .getSingleResult();
        return count > 0;
    }

    public long countByStatus(StudentStatus status) {
        return em().createNamedQuery(Student.COUNT_BY_STATUS, Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    /**
     * DYNAMIC SEARCH - the canonical use case for the Criteria API.
     *
     * <p>Both filters are optional, so there are four possible {@code WHERE}
     * clauses. The alternatives are worse:
     * <ul>
     *   <li>Four named queries - combinatorial explosion; a third filter would
     *       mean eight.</li>
     *   <li>String concatenation ({@code "WHERE 1=1" + maybe " AND ..."}) -
     *       works, but is unchecked by the compiler and one careless
     *       concatenation away from an injection vulnerability.</li>
     *   <li>Criteria - each filter appends a {@link Predicate} to a list, and the
     *       list is ANDed at the end. Type-safe and open to extension.</li>
     * </ul>
     *
     * @param nameFragment case-insensitive fragment matched against first OR last
     *                     name; {@code null} or blank means "no name filter"
     * @param status       exact status filter; {@code null} means "any status"
     */
    public Page<Student> search(String nameFragment, StudentStatus status, PageRequest pageRequest) {
        CriteriaBuilder cb = em().getCriteriaBuilder();

        // ---------------------------------------------------------------
        // 1. The data query
        // ---------------------------------------------------------------
        CriteriaQuery<Student> query = cb.createQuery(Student.class);
        Root<Student> root = query.from(Student.class);

        List<Predicate> predicates = buildPredicates(cb, root, nameFragment, status);

        query.select(root)
             // toArray with a zero-length array is the standard idiom; the JIT
             // optimises it and it is clearer than sizing the array by hand.
             .where(predicates.toArray(new Predicate[0]))
             .orderBy(cb.asc(root.get("lastName")), cb.asc(root.get("firstName")));

        List<Student> content = em().createQuery(query)
                .setFirstResult(pageRequest.getOffset())
                .setMaxResults(pageRequest.getPageSize())
                .getResultList();

        // ---------------------------------------------------------------
        // 2. The count query - the SAME predicates, applied to COUNT(*)
        //
        // A separate Root is required: a CriteriaQuery cannot be reused for a
        // different result type, and reusing the Root across queries is
        // undefined behaviour. Rebuilding the predicates against the new Root
        // is the price of type safety here.
        // ---------------------------------------------------------------
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Student> countRoot = countQuery.from(Student.class);
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, nameFragment, status);
        countQuery.select(cb.count(countRoot))
                  .where(countPredicates.toArray(new Predicate[0]));

        long total = em().createQuery(countQuery).getSingleResult();

        return Page.of(content, pageRequest, total);
    }

    /**
     * Extracted so the data query and the count query cannot drift apart - if
     * they did, the page contents and the reported total would disagree, which
     * is exactly the kind of bug nobody notices until a user complains that
     * "page 3 is empty".
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                            Root<Student> root,
                                            String nameFragment,
                                            StudentStatus status) {
        List<Predicate> predicates = new ArrayList<>();

        if (nameFragment != null && !nameFragment.trim().isEmpty()) {
            String pattern = "%" + nameFragment.trim().toLowerCase(Locale.ROOT) + "%";
            // LOWER() on both sides makes the match case-insensitive. Be aware
            // this usually prevents the database from using a plain index; a
            // production system would add a functional index on LOWER(last_name)
            // or use full-text search.
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern)
            ));
        }

        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        return predicates;
    }

    /**
     * Loads a student together with all enrollments and their courses in ONE
     * query.
     *
     * <p>This solves the N+1 SELECT PROBLEM. Without the fetch joins, reading a
     * student's transcript costs: 1 query for the student, 1 for the collection
     * of enrollments, then 1 per enrollment for its course. Thirty exams means
     * thirty-two round trips. With {@code JOIN FETCH}, one.
     *
     * <p>{@code DISTINCT} is needed because joining a collection multiplies the
     * root rows - a student with 5 enrollments comes back 5 times. In JPA 3 /
     * Hibernate 6 duplicate parent references are removed automatically, but
     * writing {@code DISTINCT} keeps the intent explicit and the query portable.
     *
     * <p>{@code LEFT JOIN} rather than an inner join, so a student with no
     * enrollments is still returned.
     */
    public Optional<Student> findByIdWithEnrollments(Long id) {
        TypedQuery<Student> query = em().createQuery(
                        "SELECT DISTINCT s FROM Student s "
                                + "LEFT JOIN FETCH s.enrollments e "
                                + "LEFT JOIN FETCH e.course c "
                                + "LEFT JOIN FETCH c.professor "
                                + "WHERE s.id = :id",
                        Student.class)
                .setParameter("id", id);
        return singleResult(query);
    }
}
