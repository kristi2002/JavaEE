package it.unicam.cs.enrollment.domain.event;

import java.time.Instant;

/**
 * Domain event fired when an exam result is registered.
 *
 * <p>A second event type exists mainly so you can see how CDI dispatches by
 * TYPE. An observer declared as {@code @Observes GradeRecordedEvent} receives
 * only these; one declared as {@code @Observes Object} would receive every event
 * in the application. Selection is entirely by the observer's parameter type,
 * with optional {@code @Qualifier} annotations to narrow it further.
 */
public final class GradeRecordedEvent {

    private final Long enrollmentId;
    private final String studentNumber;
    private final String courseCode;
    private final Integer grade;
    private final boolean withHonours;
    private final boolean passed;
    private final Instant occurredAt;

    public GradeRecordedEvent(Long enrollmentId,
                              String studentNumber,
                              String courseCode,
                              Integer grade,
                              boolean withHonours,
                              boolean passed,
                              Instant occurredAt) {
        this.enrollmentId = enrollmentId;
        this.studentNumber = studentNumber;
        this.courseCode = courseCode;
        this.grade = grade;
        this.withHonours = withHonours;
        this.passed = passed;
        this.occurredAt = occurredAt;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public Integer getGrade() {
        return grade;
    }

    public boolean isWithHonours() {
        return withHonours;
    }

    public boolean isPassed() {
        return passed;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "GradeRecordedEvent{student=" + studentNumber
                + ", course=" + courseCode
                + ", grade=" + grade
                + (withHonours ? " e lode" : "")
                + ", passed=" + passed + "}";
    }
}
