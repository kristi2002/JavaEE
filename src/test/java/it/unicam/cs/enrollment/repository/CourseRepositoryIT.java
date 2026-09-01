package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.common.PageRequest;
import it.unicam.cs.enrollment.domain.model.AcademicTitle;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Email;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.domain.model.Semester;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Integration tests for {@link CourseRepository}, focused on FETCH PLANS.
 *
 * <h2>The bug these tests exist to prevent</h2>
 * {@code GET /courses/{id}} once returned a 500 in the running server with
 * {@code LazyInitializationException: could not initialize proxy [Professor#1]}.
 *
 * <p>The query fetched the course's {@code prerequisites} — which is what the
 * enrollment rule needs — but not its {@code professor}. Nothing failed inside
 * the transaction, because a lazy load simply fires another SELECT while the
 * persistence context is open. It only broke in the REST layer, which maps the
 * entity to a DTO *after* the transaction has committed and the entity is
 * detached.
 *
 * <h2>Why {@code entityManager.clear()} is the important line</h2>
 * A naive test would load the course and assert on it while the persistence
 * context is still open — and would pass whether or not the association was
 * fetched, because the lazy load silently succeeds. Such a test gives false
 * confidence about exactly the thing it appears to check.
 *
 * <p>{@code clear()} detaches everything, reproducing the conditions the mapper
 * actually runs under. If the association was not fetched by the query, touching
 * it now throws — which is what we want the test to discover, not production.
 *
 * <p>The general principle: <b>a test must reproduce the conditions of the
 * failure it is meant to catch.</b>
 */
@DisplayName("CourseRepository (H2)")
class CourseRepositoryIT {

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private CourseRepository repository;

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private Professor professor;

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
        repository = new CourseRepository();
        repository.setEntityManager(entityManager);
        entityManager.getTransaction().begin();

        professor = new Professor("P001", "Elena", "Bianchi",
                Email.of("elena.bianchi@unicam.it"),
                AcademicTitle.FULL_PROFESSOR, "Computer Science");
        entityManager.persist(professor);
    }

    @AfterEach
    void tearDown() {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
        entityManager.close();
    }

    private Course aCourse(String code, int capacity, Semester semester, int year) {
        return new Course(code, "Title " + code, 6, capacity, semester, year, professor,
                NOW.minus(7, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
    }

    /**
     * REGRESSION TEST for the {@code LazyInitializationException} described in
     * the class comment.
     */
    @Test
    @DisplayName("detail query fetches BOTH the professor and the prerequisites")
    void shouldFetchProfessorAndPrerequisites() {
        Course prerequisite = repository.save(aCourse("CS101", 100, Semester.FALL, 2025));
        Course course = repository.save(aCourse("CS401", 30, Semester.SPRING, 2025));
        course.addPrerequisite(prerequisite);
        repository.flush();

        entityManager.clear();

        Optional<Course> found = repository.findByIdWithPrerequisites(course.getId());
        assertThat(found).isPresent();

        // Detach everything. From here on, only what the query FETCHED is usable
        // - exactly the situation the REST mapper runs in.
        entityManager.clear();

        Course detached = found.get();

        assertThat(catchThrowable(() -> detached.getProfessor().fullName()))
                .as("professor must have been fetch-joined")
                .isNull();

        assertThat(catchThrowable(() -> detached.getPrerequisites().size()))
                .as("prerequisites must have been fetch-joined")
                .isNull();

        assertThat(detached.getProfessor().fullName()).isEqualTo("Elena Bianchi");
        assertThat(detached.getPrerequisites())
                .extracting(Course::getCode)
                .containsExactly("CS101");
    }

    @Test
    @DisplayName("detail query works for a course with no prerequisites")
    void shouldHandleCourseWithoutPrerequisites() {
        Course course = repository.save(aCourse("MA101", 200, Semester.FALL, 2025));
        repository.flush();
        entityManager.clear();

        Optional<Course> found = repository.findByIdWithPrerequisites(course.getId());

        // LEFT JOIN FETCH, not an inner join: a course with no prerequisites
        // must still be returned. An inner join here would produce no row at all
        // and the endpoint would 404 on perfectly valid courses.
        assertThat(found).isPresent();
        assertThat(found.get().getPrerequisites()).isEmpty();
    }

    @Test
    @DisplayName("the open-for-enrollment query fetch-joins the professor")
    void shouldFetchProfessorForOpenCourses() {
        repository.save(aCourse("CS101", 100, Semester.FALL, 2025));
        repository.flush();
        entityManager.clear();

        java.util.List<Course> open = repository.findOpenForEnrollment(NOW);
        entityManager.clear();

        assertThat(open).hasSize(1);
        assertThat(catchThrowable(() -> open.get(0).getProfessor().fullName())).isNull();
    }

    @Test
    @DisplayName("the catalogue query fetch-joins the professor and paginates")
    void shouldFetchProfessorForCatalogue() {
        repository.save(aCourse("CS101", 100, Semester.FALL, 2025));
        repository.save(aCourse("CS201", 80, Semester.SPRING, 2025));
        repository.save(aCourse("CS301", 60, Semester.FALL, 2025));
        repository.flush();
        entityManager.clear();

        var page = repository.findByYearAndSemester(2025, Semester.FALL, PageRequest.of(0, 10));
        entityManager.clear();

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(catchThrowable(() ->
                page.getContent().forEach(c -> c.getProfessor().fullName()))).isNull();
    }

    @Test
    @DisplayName("excludes courses whose window has closed")
    void shouldExcludeClosedWindows() {
        Course closed = new Course("CS150", "Computer Architecture", 6, 90,
                Semester.FALL, 2024, professor,
                NOW.minus(400, ChronoUnit.DAYS), NOW.minus(370, ChronoUnit.DAYS));
        repository.save(closed);
        repository.save(aCourse("CS101", 100, Semester.FALL, 2025));
        repository.flush();

        assertThat(repository.findOpenForEnrollment(NOW))
                .extracting(Course::getCode)
                .containsExactly("CS101");
    }

    @Test
    @DisplayName("enforces uniqueness of (code, academicYear) but allows reuse across years")
    void shouldScopeCodeUniquenessToAcademicYear() {
        repository.save(aCourse("CS101", 100, Semester.FALL, 2024));
        repository.save(aCourse("CS101", 100, Semester.FALL, 2025));
        repository.flush();

        // The same course code in two different academic years is legitimate -
        // it is the same subject taught again. That is why the unique constraint
        // covers the PAIR, not the code alone.
        assertThat(repository.existsByCodeAndYear("CS101", 2024)).isTrue();
        assertThat(repository.existsByCodeAndYear("CS101", 2025)).isTrue();
        assertThat(repository.existsByCodeAndYear("CS101", 2026)).isFalse();
    }
}
