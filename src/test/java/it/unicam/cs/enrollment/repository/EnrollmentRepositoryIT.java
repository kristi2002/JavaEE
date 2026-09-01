package it.unicam.cs.enrollment.repository;

import it.unicam.cs.enrollment.domain.model.AcademicTitle;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Email;
import it.unicam.cs.enrollment.domain.model.Enrollment;
import it.unicam.cs.enrollment.domain.model.EnrollmentStatus;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.domain.model.Semester;
import it.unicam.cs.enrollment.domain.model.Student;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the queries that the business rules depend on.
 *
 * <p>These are the highest-value tests in the project. Each one covers a query
 * whose failure mode is SILENT: a wrong seat count does not throw, it just lets
 * an extra student into a full course. Unit tests cannot catch that, because
 * the query is exactly what they stub out.
 */
@DisplayName("EnrollmentRepository (H2)")
class EnrollmentRepositoryIT {

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private EnrollmentRepository repository;

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private Professor professor;
    private Course course;
    private Course otherCourse;

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
        repository = new EnrollmentRepository();
        repository.setEntityManager(entityManager);
        entityManager.getTransaction().begin();

        professor = new Professor("P001", "Elena", "Bianchi",
                Email.of("elena.bianchi@unicam.it"),
                AcademicTitle.FULL_PROFESSOR, "Computer Science");
        entityManager.persist(professor);

        course = new Course("CS401", "Enterprise Software Architecture", 6, 3,
                Semester.SPRING, 2025, professor,
                NOW.minus(7, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
        entityManager.persist(course);

        otherCourse = new Course("CS101", "Programming Fundamentals", 12, 100,
                Semester.FALL, 2025, professor,
                NOW.minus(7, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
        entityManager.persist(otherCourse);
    }

    @AfterEach
    void tearDown() {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
        entityManager.close();
    }

    private Student persistStudent(String number) {
        Student student = new Student(number, "Name" + number, "Surname" + number,
                Email.of("student" + number + "@studenti.unicam.it"),
                LocalDate.of(2004, 1, 1), 2023);
        entityManager.persist(student);
        return student;
    }

    private Enrollment persistEnrollment(Student student, Course target) {
        Enrollment enrollment = Enrollment.create(student, target, NOW);
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    @DisplayName("counts only ACTIVE and FAILED enrollments as occupying a seat")
    void shouldCountOnlyOccupyingStatuses() {
        persistEnrollment(persistStudent("100001"), course);                 // ACTIVE

        Enrollment failed = persistEnrollment(persistStudent("100002"), course);
        failed.recordFailure(NOW);                                           // FAILED - keeps seat

        Enrollment withdrawn = persistEnrollment(persistStudent("100003"), course);
        withdrawn.withdraw(NOW);                                             // frees the seat

        Enrollment completed = persistEnrollment(persistStudent("100004"), course);
        completed.recordPass(30, true, NOW);                                 // frees the seat

        entityManager.flush();

        // The whole capacity rule rests on this number being 2, not 4.
        assertThat(repository.countOccupiedSeats(course.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("finds an existing enrollment for a student/course pair")
    void shouldFindByStudentAndCourse() {
        Student student = persistStudent("100001");
        persistEnrollment(student, course);
        entityManager.flush();

        assertThat(repository.findByStudentAndCourse(student.getId(), course.getId()))
                .isPresent();

        // A different course must NOT match - proving both parameters are used.
        assertThat(repository.findByStudentAndCourse(student.getId(), otherCourse.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("reports a course code as completed only when the exam was passed")
    void shouldDetectCompletedCourseCode() {
        Student student = persistStudent("100001");

        Enrollment activeOne = persistEnrollment(student, otherCourse);
        entityManager.flush();

        // Still ACTIVE - the prerequisite is NOT satisfied yet.
        assertThat(repository.hasCompletedCourseCode(student.getId(), "CS101")).isFalse();

        activeOne.recordPass(24, false, NOW);
        entityManager.flush();

        // Now COMPLETED. This exercises the enum literal inside the named query,
        // which is the kind of JPQL that fails at deploy time if it is wrong.
        assertThat(repository.hasCompletedCourseCode(student.getId(), "CS101")).isTrue();

        // A code the student never took.
        assertThat(repository.hasCompletedCourseCode(student.getId(), "MA101")).isFalse();
    }

    @Test
    @DisplayName("returns seat counts for many courses in a single query")
    void shouldCountSeatsForManyCourses() {
        persistEnrollment(persistStudent("100001"), course);
        persistEnrollment(persistStudent("100002"), course);
        persistEnrollment(persistStudent("100003"), otherCourse);
        entityManager.flush();

        Map<Long, Long> counts = repository.countOccupiedSeatsByCourse(
                Arrays.asList(course.getId(), otherCourse.getId()));

        assertThat(counts).containsEntry(course.getId(), 2L);
        assertThat(counts).containsEntry(otherCourse.getId(), 1L);
    }

    @Test
    @DisplayName("omits courses with no enrollments from the batch count")
    void shouldOmitCoursesWithNoEnrollments() {
        persistEnrollment(persistStudent("100001"), course);
        entityManager.flush();

        Map<Long, Long> counts = repository.countOccupiedSeatsByCourse(
                Arrays.asList(course.getId(), otherCourse.getId()));

        // GROUP BY produces no row for a course with zero matches. This is a
        // real trap: callers MUST use getOrDefault(id, 0L). The test documents
        // the behaviour so nobody has to rediscover it in production.
        assertThat(counts).containsOnlyKeys(course.getId());
        assertThat(counts.getOrDefault(otherCourse.getId(), 0L)).isZero();
    }

    @Test
    @DisplayName("handles an empty id collection without hitting the database")
    void shouldHandleEmptyIdCollection() {
        // An empty IN () list is a syntax error on several databases, so the
        // guard clause in the repository is load-bearing, not defensive noise.
        assertThat(repository.countOccupiedSeatsByCourse(java.util.Collections.emptyList()))
                .isEmpty();
    }

    @Test
    @DisplayName("loads a student's transcript with courses fetched in one query")
    void shouldFetchTranscriptEagerly() {
        Student student = persistStudent("100001");
        persistEnrollment(student, course);
        persistEnrollment(student, otherCourse);
        entityManager.flush();
        entityManager.clear();   // empty the persistence context to force real loading

        List<Enrollment> transcript = repository.findByStudentWithCourse(student.getId());

        assertThat(transcript).hasSize(2);

        // Detach everything, then touch the fetched associations. If they had
        // NOT been fetch-joined, this would throw LazyInitializationException -
        // which is exactly the failure the REST layer would hit in production.
        entityManager.clear();
        assertThat(transcript.get(0).getCourse().getCode()).isNotBlank();
        assertThat(transcript.get(0).getCourse().getProfessor().fullName()).isNotBlank();
        assertThat(transcript.get(0).getStudent().getStudentNumber()).isEqualTo("100001");
    }

    @Test
    @DisplayName("lists a course roster filtered by status")
    void shouldListRosterByStatus() {
        persistEnrollment(persistStudent("100001"), course);
        Enrollment withdrawn = persistEnrollment(persistStudent("100002"), course);
        withdrawn.withdraw(NOW);
        entityManager.flush();

        assertThat(repository.findByCourseAndStatus(course.getId(), EnrollmentStatus.ACTIVE))
                .hasSize(1);
        assertThat(repository.findByCourseAndStatus(course.getId(), EnrollmentStatus.WITHDRAWN))
                .hasSize(1);
        assertThat(repository.findByCourseAndStatus(course.getId(), EnrollmentStatus.COMPLETED))
                .isEmpty();
    }
}
