package it.unicam.cs.enrollment.service;

import it.unicam.cs.enrollment.domain.event.EnrollmentCreatedEvent;
import it.unicam.cs.enrollment.domain.event.GradeRecordedEvent;
import it.unicam.cs.enrollment.domain.model.AcademicTitle;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Email;
import it.unicam.cs.enrollment.domain.model.Enrollment;
import it.unicam.cs.enrollment.domain.model.EnrollmentStatus;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.domain.model.Semester;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.exception.BusinessRuleViolationException;
import it.unicam.cs.enrollment.exception.DuplicateResourceException;
import it.unicam.cs.enrollment.exception.ResourceNotFoundException;
import it.unicam.cs.enrollment.repository.CourseRepository;
import it.unicam.cs.enrollment.repository.EnrollmentRepository;
import it.unicam.cs.enrollment.repository.StudentRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnrollmentService} - every business rule, in isolation.
 *
 * <h2>What "unit test" means here</h2>
 * Exactly one class is under test. Its collaborators are all MOCKS, so:
 * <ul>
 *   <li>no database, no application server, no Docker - the whole class runs in
 *       well under a second;</li>
 *   <li>a failure can only mean the service is wrong, never that something else
 *       in the stack is;</li>
 *   <li>situations that are awkward to arrange for real - a full course, a
 *       closed window - are one line of stubbing.</li>
 * </ul>
 *
 * <h2>Why constructor injection made this possible</h2>
 * The service is created with {@code new}, passing seven mocks. No CDI, no
 * reflection, no {@code @InjectMocks} magic. That is the practical payoff of
 * constructor injection, and the reason it is worth insisting on.
 *
 * <h2>The fixed clock</h2>
 * {@code Clock.fixed} freezes time at a known instant, so tests of the
 * enrollment window are exact and deterministic rather than "usually passes".
 * A test that depends on the real clock is a test that will fail one day at
 * midnight, in a different timezone, for no reason anyone can reproduce.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService")
class EnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Long STUDENT_ID = 1L;
    private static final Long COURSE_ID = 2L;

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private Event<EnrollmentCreatedEvent> enrollmentCreatedEvent;
    @Mock
    private Event<GradeRecordedEvent> gradeRecordedEvent;
    @Mock
    private Logger log;

    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(
                studentRepository,
                courseRepository,
                enrollmentRepository,
                enrollmentCreatedEvent,
                gradeRecordedEvent,
                Clock.fixed(NOW, ZoneOffset.UTC),
                log);
    }

    // ------------------------------------------------------------------
    // Test-data builders
    // ------------------------------------------------------------------

    private Professor aProfessor() {
        return new Professor("P001", "Elena", "Bianchi",
                Email.of("elena.bianchi@unicam.it"),
                AcademicTitle.FULL_PROFESSOR, "Computer Science");
    }

    private Student anActiveStudent() {
        return new Student("100001", "Luca", "Ferrari",
                Email.of("luca.ferrari@studenti.unicam.it"),
                LocalDate.of(2004, 3, 14), 2023);
    }

    /** A course whose enrollment window is open at {@link #NOW}. */
    private Course anOpenCourse(int capacity) {
        return new Course("CS401", "Enterprise Software Architecture", 6, capacity,
                Semester.SPRING, 2025, aProfessor(),
                NOW.minus(7, ChronoUnit.DAYS),
                NOW.plus(7, ChronoUnit.DAYS));
    }

    @Nested
    @DisplayName("enroll")
    class Enroll {

        @Test
        @DisplayName("creates an ACTIVE enrollment when every rule passes")
        void shouldEnrollWhenAllRulesPass() {
            // ---- ARRANGE
            Student student = anActiveStudent();
            Course course = anOpenCourse(30);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(10L);

            // ---- ACT
            Enrollment result = service.enroll(STUDENT_ID, COURSE_ID);

            // ---- ASSERT (state)
            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(result.getEnrolledAt()).isEqualTo(NOW);
            assertThat(result.getStudent()).isSameAs(student);
            assertThat(result.getCourse()).isSameAs(course);

            // ---- ASSERT (interactions)
            // Verifying the SAVE matters: without it the test would still pass
            // if someone deleted the persistence call, since the returned object
            // is built in memory either way.
            verify(enrollmentRepository).save(result);

            // ArgumentCaptor grabs the object the service actually passed, so we
            // can assert on its contents rather than merely that "something" was
            // fired.
            ArgumentCaptor<EnrollmentCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(EnrollmentCreatedEvent.class);
            verify(enrollmentCreatedEvent).fire(eventCaptor.capture());

            EnrollmentCreatedEvent event = eventCaptor.getValue();
            assertThat(event.getStudentNumber()).isEqualTo("100001");
            assertThat(event.getCourseCode()).isEqualTo("CS401");
            assertThat(event.getOccurredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("takes a PESSIMISTIC lock on the course before checking capacity")
        void shouldLockTheCourseRow() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(30);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(0L);

            service.enroll(STUDENT_ID, COURSE_ID);

            // This assertion protects the concurrency fix. If someone "optimises"
            // the locking read into a plain findById, the capacity rule silently
            // becomes racy - a bug that would almost never show up in manual
            // testing. The test makes the intent enforceable.
            verify(courseRepository).findByIdWithPessimisticLock(COURSE_ID);
            verify(courseRepository, never()).findById(any());
        }

        @Test
        @DisplayName("rejects an unknown student with 404 semantics")
        void shouldRejectUnknownStudent() {
            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Student");

            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a suspended student")
        void shouldRejectSuspendedStudent() {
            Student student = anActiveStudent();
            student.suspend();

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "STUDENT_NOT_ELIGIBLE");

            // Fails BEFORE taking the expensive database lock - the ordering of
            // the checks in the service is deliberate, so it is worth asserting.
            verify(courseRepository, never()).findByIdWithPessimisticLock(any());
        }

        @Test
        @DisplayName("rejects enrollment when the window is closed")
        void shouldRejectClosedWindow() {
            Student student = anActiveStudent();
            Course closed = new Course("CS150", "Computer Architecture", 6, 90,
                    Semester.FALL, 2024, aProfessor(),
                    NOW.minus(400, ChronoUnit.DAYS),
                    NOW.minus(370, ChronoUnit.DAYS));

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(closed));

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "ENROLLMENT_WINDOW_CLOSED");
        }

        @Test
        @DisplayName("rejects a duplicate enrollment")
        void shouldRejectDuplicate() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(30);
            Enrollment existing = Enrollment.create(student, course, NOW);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already enrolled");
        }

        @Test
        @DisplayName("rejects enrollment when the course is full")
        void shouldRejectWhenFull() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(3);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(3L);

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "COURSE_FULL");

            verify(enrollmentRepository, never()).save(any());
        }

        /**
         * BOUNDARY: capacity 3 with 2 seats taken must SUCCEED. The full test
         * above and this one together pin down the exact comparison; either
         * alone would still pass with an off-by-one {@code >} versus {@code >=}.
         */
        @Test
        @DisplayName("allows the very last seat to be taken")
        void shouldAllowTheLastSeat() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(3);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(2L);

            Enrollment result = service.enroll(STUDENT_ID, COURSE_ID);

            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        }

        @Test
        @DisplayName("rejects enrollment when a prerequisite has not been passed")
        void shouldRejectUnmetPrerequisites() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(30);

            Course prerequisite = new Course("CS101", "Programming Fundamentals", 12, 100,
                    Semester.FALL, 2025, aProfessor(),
                    NOW.minus(7, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
            course.addPrerequisite(prerequisite);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(0L);
            when(enrollmentRepository.hasCompletedCourseCode(any(), eq("CS101")))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.enroll(STUDENT_ID, COURSE_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "PREREQUISITES_NOT_MET")
                    // The message must name WHICH prerequisite is missing.
                    // "Prerequisites not met" alone leaves the user guessing.
                    .hasMessageContaining("CS101");
        }

        @Test
        @DisplayName("allows enrollment once the prerequisite has been passed")
        void shouldAllowWhenPrerequisitesMet() {
            Student student = anActiveStudent();
            Course course = anOpenCourse(30);

            Course prerequisite = new Course("CS101", "Programming Fundamentals", 12, 100,
                    Semester.FALL, 2025, aProfessor(),
                    NOW.minus(7, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
            course.addPrerequisite(prerequisite);

            when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
            when(courseRepository.findByIdWithPessimisticLock(COURSE_ID))
                    .thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudentAndCourse(STUDENT_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countOccupiedSeats(COURSE_ID)).thenReturn(0L);
            when(enrollmentRepository.hasCompletedCourseCode(any(), eq("CS101")))
                    .thenReturn(true);

            assertThat(service.enroll(STUDENT_ID, COURSE_ID).getStatus())
                    .isEqualTo(EnrollmentStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("recordPass")
    class RecordPass {

        private Enrollment anEnrollment() {
            return Enrollment.create(anActiveStudent(), anOpenCourse(30), NOW);
        }

        @Test
        @DisplayName("stores the grade and fires an event")
        void shouldRecordGrade() {
            Enrollment enrollment = anEnrollment();
            when(enrollmentRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(enrollment));

            Enrollment result = service.recordPass(3L, 30, true);

            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
            assertThat(result.getGrade()).isEqualTo(30);
            assertThat(result.isWithHonours()).isTrue();
            assertThat(result.getCompletedAt()).isEqualTo(NOW);

            ArgumentCaptor<GradeRecordedEvent> captor =
                    ArgumentCaptor.forClass(GradeRecordedEvent.class);
            verify(gradeRecordedEvent).fire(captor.capture());
            assertThat(captor.getValue().isPassed()).isTrue();
            assertThat(captor.getValue().isWithHonours()).isTrue();
        }

        /**
         * Verifies the EXCEPTION TRANSLATION between layers: the entity throws
         * {@code IllegalArgumentException}, and the service must convert it into
         * the application's own type with a stable error code, because that is
         * what the REST layer maps to a 409.
         */
        @Test
        @DisplayName("translates a domain error into a BusinessRuleViolationException")
        void shouldTranslateDomainErrors() {
            Enrollment enrollment = anEnrollment();
            when(enrollmentRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> service.recordPass(3L, 28, true))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "INVALID_GRADE");
        }

        @Test
        @DisplayName("reports an unknown enrollment as not found")
        void shouldRejectUnknownEnrollment() {
            when(enrollmentRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordPass(99L, 30, false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
