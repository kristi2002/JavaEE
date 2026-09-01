package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.common.PageRequest;
import it.unicam.cs.enrollment.domain.model.Email;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.domain.model.StudentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTEGRATION TESTS for {@link StudentRepository}, against a real H2 database.
 *
 * <h2>Why the name ends in {@code IT}</h2>
 * Maven's Failsafe plugin runs {@code *IT} classes during {@code mvn verify},
 * while Surefire runs {@code *Test} classes during {@code mvn test}. The split
 * is a convention worth keeping: developers run the fast unit tests constantly,
 * and the slower integration tests run before anything is packaged. Naming is
 * the whole mechanism - there is no other configuration.
 *
 * <h2>What only an integration test can catch</h2>
 * A mocked repository always returns what you told it to. It cannot tell you
 * that:
 * <ul>
 *   <li>your JPQL has a typo, or references a field that no longer exists;</li>
 *   <li>{@code LOWER()} behaves as you expect for the search;</li>
 *   <li>the unique constraint is actually created;</li>
 *   <li>{@code setFirstResult}/{@code setMaxResults} produce the page you meant.</li>
 * </ul>
 * These are precisely the bugs that unit tests miss and users find.
 *
 * <h2>Test isolation</h2>
 * Each test runs inside a transaction that is ROLLED BACK in {@code @AfterEach}.
 * Nothing a test writes is visible to any other, so they can run in any order.
 * Tests that depend on execution order are the classic source of a suite that
 * "only fails in CI".
 */
@DisplayName("StudentRepository (H2)")
class StudentRepositoryIT {

    /**
     * The EntityManagerFactory is EXPENSIVE - it parses the mappings, validates
     * every query and builds the schema. Creating one per test would make the
     * class take minutes instead of seconds, so it is created once in
     * {@code @BeforeAll} and shared.
     *
     * <p>The EntityManager, by contrast, is cheap and must NOT be shared: it
     * carries the persistence context, which is exactly the state we want reset
     * between tests.
     */
    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private StudentRepository repository;

    @BeforeAll
    static void createFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("enrollmentTestPU");
    }

    @AfterAll
    static void closeFactory() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();

        repository = new StudentRepository();
        // The package-private seam declared on AbstractJpaRepository. Accessible
        // because this test lives in the same package - which is why the test
        // source tree mirrors the main one.
        repository.setEntityManager(entityManager);

        // RESOURCE_LOCAL: no container, so the test drives the transaction.
        entityManager.getTransaction().begin();
    }

    @AfterEach
    void tearDown() {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
        entityManager.close();
    }

    private Student aStudent(String number, String first, String last, String email) {
        return new Student(number, first, last, Email.of(email),
                LocalDate.of(2004, 3, 14), 2023);
    }

    @Test
    @DisplayName("persists a student and assigns a generated id")
    void shouldPersistAndGenerateId() {
        Student student = aStudent("100001", "Luca", "Ferrari", "luca.ferrari@studenti.unicam.it");

        assertThat(student.isNew()).isTrue();

        repository.save(student);
        repository.flush();

        assertThat(student.getId()).isNotNull();
        assertThat(student.isNew()).isFalse();
        // @PrePersist populated the audit column without anyone calling it.
        assertThat(student.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("finds a student by matricola using the named query")
    void shouldFindByStudentNumber() {
        repository.save(aStudent("100001", "Luca", "Ferrari", "luca.ferrari@studenti.unicam.it"));
        repository.flush();

        Optional<Student> found = repository.findByStudentNumber("100001");

        assertThat(found).isPresent();
        assertThat(found.get().fullName()).isEqualTo("Luca Ferrari");
    }

    @Test
    @DisplayName("returns an empty Optional rather than throwing when nothing matches")
    void shouldReturnEmptyForUnknownStudentNumber() {
        // The behaviour AbstractJpaRepository.singleResult exists to provide:
        // getSingleResult() would have thrown NoResultException here.
        assertThat(repository.findByStudentNumber("999999")).isEmpty();
    }

    @Test
    @DisplayName("normalises email to lower case, so the uniqueness check works")
    void shouldNormaliseEmail() {
        repository.save(aStudent("100001", "Luca", "Ferrari", "Luca.FERRARI@studenti.unicam.it"));
        repository.flush();

        // Stored lower-cased by Email.of, so a lower-case lookup finds it.
        assertThat(repository.existsByEmail("luca.ferrari@studenti.unicam.it")).isTrue();
    }

    @Test
    @DisplayName("matches a name fragment case-insensitively, on either name")
    void shouldSearchByNameFragment() {
        repository.save(aStudent("100001", "Luca", "Ferrari", "luca@studenti.unicam.it"));
        repository.save(aStudent("100002", "Sofia", "Greco", "sofia@studenti.unicam.it"));
        repository.save(aStudent("100003", "Matteo", "Ferrarini", "matteo@studenti.unicam.it"));
        repository.flush();

        // Lower case input against mixed-case data proves LOWER() is applied to
        // both sides of the comparison.
        Page<Student> page = repository.search("ferrar", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Student::getLastName)
                .containsExactly("Ferrari", "Ferrarini");   // ordered by last name

        // A fragment matching the FIRST name must work too - the query ORs both.
        assertThat(repository.search("sofia", null, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("combines the name and status filters")
    void shouldCombineFilters() {
        Student active = aStudent("100001", "Luca", "Ferrari", "luca@studenti.unicam.it");
        Student suspended = aStudent("100002", "Marco", "Ferrari", "marco@studenti.unicam.it");
        suspended.suspend();

        repository.save(active);
        repository.save(suspended);
        repository.flush();

        assertThat(repository.search("ferrari", StudentStatus.ACTIVE, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);

        assertThat(repository.search("ferrari", StudentStatus.SUSPENDED, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);

        // No status filter -> both.
        assertThat(repository.search("ferrari", null, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("paginates correctly and reports the true total")
    void shouldPaginate() {
        for (int i = 1; i <= 7; i++) {
            repository.save(aStudent(String.format("10000%d", i),
                    "Name" + i, "Surname" + i, "student" + i + "@studenti.unicam.it"));
        }
        repository.flush();

        Page<Student> firstPage = repository.search(null, null, PageRequest.of(0, 3));

        // The page holds 3 items, but totalElements must report ALL 7. Getting
        // this wrong - by counting the page instead of the query - is the single
        // most common pagination bug.
        assertThat(firstPage.getContent()).hasSize(3);
        assertThat(firstPage.getTotalElements()).isEqualTo(7);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.hasNext()).isTrue();

        Page<Student> lastPage = repository.search(null, null, PageRequest.of(2, 3));
        assertThat(lastPage.getContent()).hasSize(1);   // 7 = 3 + 3 + 1
        assertThat(lastPage.isLast()).isTrue();
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("counts by status")
    void shouldCountByStatus() {
        Student a = aStudent("100001", "Luca", "Ferrari", "a@studenti.unicam.it");
        Student b = aStudent("100002", "Sofia", "Greco", "b@studenti.unicam.it");
        Student c = aStudent("100003", "Matteo", "Esposito", "c@studenti.unicam.it");
        c.suspend();

        repository.save(a);
        repository.save(b);
        repository.save(c);
        repository.flush();

        assertThat(repository.countByStatus(StudentStatus.ACTIVE)).isEqualTo(2);
        assertThat(repository.countByStatus(StudentStatus.SUSPENDED)).isEqualTo(1);
        assertThat(repository.countByStatus(StudentStatus.GRADUATED)).isZero();
    }

    /**
     * REGRESSION TEST.
     *
     * <p>This exact call used to fail at runtime with
     * {@code "setFirstResult()/setMaxResults() specified with collection fetch
     * join"}, because the shared {@code singleResult} helper applied a
     * {@code LIMIT} and this query fetch-joins a COLLECTION. It passed every
     * test at the time, because the test persistence unit did not have
     * {@code fail_on_pagination_over_collection_fetch} enabled the way the real
     * one did.
     *
     * <p>Writing the test that would have caught a bug is the most valuable
     * moment to add one: it is the only way to be sure the fix works AND that
     * nobody reintroduces it.
     */
    @Test
    @DisplayName("loads a student together with enrollments via a collection fetch join")
    void shouldLoadStudentWithEnrollments() {
        Student student = aStudent("100001", "Luca", "Ferrari", "luca@studenti.unicam.it");
        repository.save(student);
        repository.flush();

        Optional<Student> found = repository.findByIdWithEnrollments(student.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStudentNumber()).isEqualTo("100001");
        assertThat(found.get().getEnrollments()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for an unknown id even with a collection fetch join")
    void shouldReturnEmptyForUnknownIdWithEnrollments() {
        assertThat(repository.findByIdWithEnrollments(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("increments the @Version column on update")
    void shouldIncrementVersionOnUpdate() {
        Student student = aStudent("100001", "Luca", "Ferrari", "luca@studenti.unicam.it");
        repository.save(student);
        repository.flush();

        long initialVersion = student.getVersion();

        student.setLastName("Ferrari-Rossi");
        repository.flush();

        // Proof that optimistic locking is actually wired up. If @Version were
        // removed, this test fails and tells you concurrent updates are no
        // longer being detected.
        assertThat(student.getVersion()).isEqualTo(initialVersion + 1);
    }
}
