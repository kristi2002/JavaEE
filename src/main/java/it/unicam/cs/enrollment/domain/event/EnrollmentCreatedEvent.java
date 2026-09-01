package it.unicam.cs.enrollment.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * A DOMAIN EVENT: "a student successfully enrolled in a course".
 *
 * <h2>What a domain event is for</h2>
 * Without events, {@code EnrollmentService.enroll()} would have to know about
 * every side effect anyone ever wants: send a confirmation email, update a
 * dashboard, write to an audit log, notify the professor. Each new requirement
 * means editing the service, and the service slowly turns into a dependency
 * magnet.
 *
 * <p>With events, the service says what HAPPENED and stops caring what anyone
 * does about it. Observers subscribe independently. Adding a new side effect
 * means adding a new class and touching nothing else - which is the Open/Closed
 * Principle in practice, and CDI gives it to you for free via
 * {@code Event<T>.fire()} and {@code @Observes}.
 *
 * <h2>Design rules for events</h2>
 * <ul>
 *   <li><b>Immutable.</b> Several observers receive the same instance; if one
 *       could mutate it, the others would see different data depending on
 *       ordering. All fields are {@code final} with no setters.</li>
 *   <li><b>Past tense.</b> {@code EnrollmentCreated}, not
 *       {@code CreateEnrollment}. An event is a fact that already happened; a
 *       command is a request that might be refused. Naming keeps the two apart.</li>
 *   <li><b>Carry ids and copies, not entities.</b> Passing a managed JPA entity
 *       to an asynchronous observer is a classic bug: by the time it runs, the
 *       persistence context is closed and every lazy field throws
 *       {@code LazyInitializationException}.</li>
 * </ul>
 */
public final class EnrollmentCreatedEvent {

    private final Long enrollmentId;
    private final Long studentId;
    private final String studentNumber;
    private final String studentEmail;
    private final Long courseId;
    private final String courseCode;
    private final String courseTitle;
    private final Instant occurredAt;

    public EnrollmentCreatedEvent(Long enrollmentId,
                                  Long studentId,
                                  String studentNumber,
                                  String studentEmail,
                                  Long courseId,
                                  String courseCode,
                                  String courseTitle,
                                  Instant occurredAt) {
        this.enrollmentId = enrollmentId;
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.studentEmail = studentEmail;
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.courseTitle = courseTitle;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "EnrollmentCreatedEvent{student=" + studentNumber
                + ", course=" + courseCode
                + ", at=" + occurredAt + "}";
    }
}
