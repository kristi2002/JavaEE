package it.unicam.cs.enrollment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for the {@link Enrollment} state machine and grading rules.
 *
 * <h2>What makes these good tests</h2>
 * <ul>
 *   <li><b>No mocks, no framework, no database.</b> {@code Enrollment} is plain
 *       Java, so the test is plain Java. Tests this cheap run in milliseconds
 *       and never break for environmental reasons - which is the whole argument
 *       for keeping business rules out of the framework layers.</li>
 *   <li><b>{@code @Nested} classes</b> group related cases, so the test report
 *       reads as a specification of the type rather than a flat list.</li>
 *   <li><b>{@code @DisplayName}</b> gives each case a sentence. When one fails
 *       in CI, "honours can only be awarded with a grade of 30" tells you what
 *       broke without opening the file.</li>
 * </ul>
 *
 * <h2>The naming convention used throughout</h2>
 * {@code should<ExpectedBehaviour>When<Condition>}, and the body follows
 * ARRANGE - ACT - ASSERT (also called GIVEN - WHEN - THEN). Keeping to a shape
 * means a reader never has to work out which part of a test is setup.
 */
@DisplayName("Enrollment")
class EnrollmentTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    /**
     * Test-data builder. Every test needs an enrollment, and repeating six lines
     * of construction in each one buries the single line that actually matters.
     *
     * <p>On a real project this grows into an OBJECT MOTHER or a builder class
     * per aggregate. It is one of the highest-return investments in a test suite:
     * setup shrinks, and a change to a constructor is fixed in one place instead
     * of two hundred.
     */
    private Enrollment anActiveEnrollment() {
        Professor professor = new Professor("P001", "Elena", "Bianchi",
                Email.of("elena.bianchi@unicam.it"),
                AcademicTitle.FULL_PROFESSOR, "Computer Science");

        Course course = new Course("CS101", "Programming Fundamentals", 12, 100,
                Semester.FALL, 2025, professor,
                NOW.minus(10, ChronoUnit.DAYS),
                NOW.plus(10, ChronoUnit.DAYS));

        Student student = new Student("100001", "Luca", "Ferrari",
                Email.of("luca.ferrari@studenti.unicam.it"),
                LocalDate.of(2004, 3, 14), 2023);

        return Enrollment.create(student, course, NOW);
    }

    @Nested
    @DisplayName("when created")
    class WhenCreated {

        @Test
        @DisplayName("starts in ACTIVE status with no grade")
        void shouldStartActiveWithoutGrade() {
            Enrollment enrollment = anActiveEnrollment();

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(enrollment.getGrade()).isNull();
            assertThat(enrollment.isWithHonours()).isFalse();
            assertThat(enrollment.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("wires both sides of the student association")
        void shouldSynchroniseBothSidesOfTheAssociation() {
            Enrollment enrollment = anActiveEnrollment();

            // The factory must keep the object graph consistent - see the
            // discussion on Student.addEnrollment.
            assertThat(enrollment.getStudent().getEnrollments()).contains(enrollment);
        }
    }

    @Nested
    @DisplayName("when recording a pass")
    class WhenRecordingAPass {

        @Test
        @DisplayName("moves to COMPLETED and stores the grade")
        void shouldCompleteWithGrade() {
            Enrollment enrollment = anActiveEnrollment();

            enrollment.recordPass(27, false, NOW);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
            assertThat(enrollment.getGrade()).isEqualTo(27);
            assertThat(enrollment.getCompletedAt()).isEqualTo(NOW);
            assertThat(enrollment.formattedGrade()).isEqualTo("27");
        }

        @Test
        @DisplayName("accepts honours with a grade of exactly 30")
        void shouldAcceptHonoursWithThirty() {
            Enrollment enrollment = anActiveEnrollment();

            enrollment.recordPass(30, true, NOW);

            assertThat(enrollment.isWithHonours()).isTrue();
            assertThat(enrollment.formattedGrade()).isEqualTo("30 e lode");
            assertThat(enrollment.isHonoursConsistent()).isTrue();
        }

        @Test
        @DisplayName("rejects honours with a grade below 30")
        void shouldRejectHonoursBelowThirty() {
            Enrollment enrollment = anActiveEnrollment();

            // assertThatThrownBy asserts BOTH the exception type and its message.
            // Checking the message matters: it is the message a user will read,
            // and an exception with a useless message is barely better than none.
            assertThatThrownBy(() -> enrollment.recordPass(29, true, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Honours");
        }

        /**
         * BOUNDARY TESTING. Off-by-one errors cluster at the edges of a range, so
         * the values worth testing are min-1, min, max, max+1 - not a random 25.
         */
        @Test
        @DisplayName("rejects grades outside 18-30")
        void shouldRejectOutOfRangeGrades() {
            assertThatThrownBy(() -> anActiveEnrollment().recordPass(17, false, NOW))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> anActiveEnrollment().recordPass(31, false, NOW))
                    .isInstanceOf(IllegalArgumentException.class);

            // The boundaries themselves must be ACCEPTED. catchThrowable returns
            // null when nothing is thrown, which is the cleanest way to assert
            // "this must NOT fail".
            assertThat(catchThrowable(() -> anActiveEnrollment().recordPass(18, false, NOW)))
                    .isNull();
            assertThat(catchThrowable(() -> anActiveEnrollment().recordPass(30, false, NOW)))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("state machine")
    class StateMachine {

        @Test
        @DisplayName("refuses to grade an already completed enrollment")
        void shouldRefuseDoubleGrading() {
            Enrollment enrollment = anActiveEnrollment();
            enrollment.recordPass(28, false, NOW);

            assertThatThrownBy(() -> enrollment.recordPass(30, false, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED -> COMPLETED");
        }

        @Test
        @DisplayName("allows a retake only after a failure")
        void shouldAllowRetakeOnlyAfterFailure() {
            Enrollment failed = anActiveEnrollment();
            failed.recordFailure(NOW);
            assertThat(failed.getStatus()).isEqualTo(EnrollmentStatus.FAILED);

            failed.retake();
            assertThat(failed.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(failed.getCompletedAt()).isNull();

            // From ACTIVE, retake() is not a legal transition.
            assertThatThrownBy(failed::retake)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("treats WITHDRAWN as terminal")
        void shouldTreatWithdrawnAsTerminal() {
            Enrollment enrollment = anActiveEnrollment();
            enrollment.withdraw(NOW);

            assertThat(enrollment.getStatus().isTerminal()).isTrue();
            assertThatThrownBy(() -> enrollment.recordPass(30, false, NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("counts ACTIVE and FAILED as occupying a seat")
        void shouldReportSeatOccupancy() {
            // The capacity rule depends entirely on this: a student who failed
            // still holds their place for the retake.
            assertThat(EnrollmentStatus.ACTIVE.occupiesSeat()).isTrue();
            assertThat(EnrollmentStatus.FAILED.occupiesSeat()).isTrue();
            assertThat(EnrollmentStatus.WITHDRAWN.occupiesSeat()).isFalse();
            assertThat(EnrollmentStatus.COMPLETED.occupiesSeat()).isFalse();
        }
    }
}
