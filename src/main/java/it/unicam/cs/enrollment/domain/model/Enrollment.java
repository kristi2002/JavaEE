package it.unicam.cs.enrollment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * The link between a {@link Student} and a {@link Course} - and the reason this
 * model is worth studying.
 *
 * <h2>Why an entity and not just {@code @ManyToMany}?</h2>
 * A plain {@code @ManyToMany} between Student and Course would give you a join
 * table with two foreign keys and nothing else. But the relationship itself
 * carries data: when did the student enrol, what grade did they get, is the
 * enrollment still active?
 *
 * <p>The moment an association has ATTRIBUTES OF ITS OWN, it stops being a
 * relationship and becomes an entity in its own right. This pattern is variously
 * called an "association entity", "join entity" or "link entity", and
 * recognising when you need one is one of the more valuable modelling skills.
 *
 * <pre>
 *   Student  1 ────&lt; Enrollment &gt;──── 1  Course
 *                    + status
 *                    + enrolledAt
 *                    + grade
 * </pre>
 *
 * <h2>Identity</h2>
 * We give it a surrogate {@code id} inherited from {@link BaseEntity} plus a
 * UNIQUE constraint on {@code (student_id, course_id)}. The alternative -
 * a composite key via {@code @IdClass} or {@code @EmbeddedId} - is more
 * "correct" relationally but painful in practice: every reference to the row
 * needs two values. The surrogate-key-plus-unique-constraint combination gets
 * both properties and is what most teams do.
 */
@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                // This is the LAST LINE OF DEFENCE against double enrollment.
                // The service checks for duplicates too, but between its check
                // and its insert another transaction could slip in. Only the
                // database can make the rule airtight, because only the database
                // sees all transactions. Always back a business rule that must
                // never be violated with a real constraint.
                name = "uk_enrollments_student_course",
                columnNames = {"student_id", "course_id"}
        ),
        indexes = {
                @Index(name = "idx_enrollments_course_status", columnList = "course_id, status"),
                @Index(name = "idx_enrollments_student", columnList = "student_id")
        }
)
@NamedQuery(
        name = Enrollment.FIND_BY_STUDENT_AND_COURSE,
        query = "SELECT e FROM Enrollment e "
                + "WHERE e.student.id = :studentId AND e.course.id = :courseId"
)
@NamedQuery(
        name = Enrollment.COUNT_OCCUPIED_SEATS,
        query = "SELECT COUNT(e) FROM Enrollment e "
                + "WHERE e.course.id = :courseId AND e.status IN :occupyingStatuses"
)
@NamedQuery(
        name = Enrollment.FIND_BY_STUDENT_WITH_COURSE,
        // Every association the API response needs is fetched here, in one
        // query. Deciding the FETCH PLAN per use case - rather than making
        // associations EAGER globally - is the difference between an
        // application that scales and one that does not.
        query = "SELECT e FROM Enrollment e "
                + "JOIN FETCH e.student "
                + "JOIN FETCH e.course c "
                + "JOIN FETCH c.professor "
                + "WHERE e.student.id = :studentId ORDER BY e.enrolledAt DESC"
)
@NamedQuery(
        name = Enrollment.FIND_BY_ID_WITH_DETAILS,
        query = "SELECT e FROM Enrollment e "
                + "JOIN FETCH e.student "
                + "JOIN FETCH e.course c "
                + "JOIN FETCH c.professor "
                + "WHERE e.id = :id"
)
@NamedQuery(
        name = Enrollment.HAS_COMPLETED_COURSE_CODE,
        query = "SELECT COUNT(e) FROM Enrollment e "
                + "WHERE e.student.id = :studentId "
                + "AND e.course.code = :courseCode "
                + "AND e.status = it.unicam.cs.enrollment.domain.model.EnrollmentStatus.COMPLETED"
)
public class Enrollment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public static final String FIND_BY_STUDENT_AND_COURSE = "Enrollment.findByStudentAndCourse";
    public static final String COUNT_OCCUPIED_SEATS = "Enrollment.countOccupiedSeats";
    public static final String FIND_BY_STUDENT_WITH_COURSE = "Enrollment.findByStudentWithCourse";
    public static final String FIND_BY_ID_WITH_DETAILS = "Enrollment.findByIdWithDetails";
    public static final String HAS_COMPLETED_COURSE_CODE = "Enrollment.hasCompletedCourseCode";

    /** Lowest passing grade in the Italian university system. */
    public static final int MIN_PASSING_GRADE = 18;

    /** Highest grade; only a 30 may additionally carry honours ({@code lode}). */
    public static final int MAX_GRADE = 30;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_student"))
    private Student student;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_course"))
    private Course course;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @NotNull
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    /** Set when the enrollment reaches a terminal state. Null while ACTIVE. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * The Italian grade, 18-30. {@code Integer} rather than {@code int} because
     * "not yet graded" is a real state and needs to be representable. This is
     * one of the few places where a boxed primitive is the right call: a
     * primitive {@code int} would default to 0, which is not a valid grade and
     * would be indistinguishable from "no grade yet".
     */
    @Min(MIN_PASSING_GRADE)
    @Max(MAX_GRADE)
    @Column(name = "grade")
    private Integer grade;

    /** {@code 30 e lode} - the distinction awarded on top of a perfect 30. */
    @Column(name = "with_honours", nullable = false)
    private boolean withHonours;

    protected Enrollment() {
        // required by JPA
    }

    /**
     * Package-private on purpose. An {@code Enrollment} is only ever created
     * through {@link #create(Student, Course, Instant)} or, in the wider
     * application, through {@code EnrollmentService.enroll(...)} which enforces
     * the business rules. Restricting visibility is how you make "the wrong way"
     * unavailable rather than merely discouraged.
     */
    private Enrollment(Student student, Course course, Instant enrolledAt) {
        this.student = student;
        this.course = course;
        this.enrolledAt = enrolledAt;
        this.status = EnrollmentStatus.ACTIVE;
        this.withHonours = false;
    }

    /**
     * FACTORY METHOD. Also wires both sides of the Student association so the
     * in-memory graph is immediately consistent.
     */
    public static Enrollment create(Student student, Course course, Instant enrolledAt) {
        Objects.requireNonNull(student, "student must not be null");
        Objects.requireNonNull(course, "course must not be null");
        Objects.requireNonNull(enrolledAt, "enrolledAt must not be null");

        Enrollment enrollment = new Enrollment(student, course, enrolledAt);
        student.addEnrollment(enrollment);
        return enrollment;
    }

    // ------------------------------------------------------------------
    // State transitions
    //
    // Every one of these goes through EnrollmentStatus.canTransitionTo, so the
    // state machine is enforced in exactly one place. Note they throw
    // IllegalStateException, a plain JDK exception - the DOMAIN layer stays
    // free of framework and application-specific types. The service layer
    // translates these into the application's own exception hierarchy.
    // ------------------------------------------------------------------

    /**
     * Records a passing exam result.
     *
     * @param grade       18-30
     * @param withHonours {@code lode}; only legal with a grade of exactly 30
     * @param at          the instant the result was recorded
     */
    public void recordPass(int grade, boolean withHonours, Instant at) {
        transitionTo(EnrollmentStatus.COMPLETED);

        if (grade < MIN_PASSING_GRADE || grade > MAX_GRADE) {
            throw new IllegalArgumentException(
                    "A passing grade must be between " + MIN_PASSING_GRADE
                            + " and " + MAX_GRADE + ", got " + grade);
        }
        if (withHonours && grade != MAX_GRADE) {
            throw new IllegalArgumentException(
                    "Honours (lode) can only be awarded with a grade of " + MAX_GRADE);
        }

        this.grade = grade;
        this.withHonours = withHonours;
        this.completedAt = at;
    }

    /** Records a failed exam. The student keeps the seat and may retake. */
    public void recordFailure(Instant at) {
        transitionTo(EnrollmentStatus.FAILED);
        this.grade = null;
        this.withHonours = false;
        this.completedAt = at;
    }

    /** The student leaves the course. Terminal - the seat is released. */
    public void withdraw(Instant at) {
        transitionTo(EnrollmentStatus.WITHDRAWN);
        this.completedAt = at;
    }

    /** After a failure, the student re-activates the enrollment to retake the exam. */
    public void retake() {
        transitionTo(EnrollmentStatus.ACTIVE);
        this.completedAt = null;
    }

    /**
     * The single guarded mutation point for {@link #status}. Any illegal
     * transition fails here with a message that names both states, which is the
     * difference between a five-second and a fifty-minute debugging session.
     */
    private void transitionTo(EnrollmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal enrollment transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    // ------------------------------------------------------------------
    // Cross-field validation
    // ------------------------------------------------------------------

    /**
     * CLASS-LEVEL (cross-field) VALIDATION with {@code @AssertTrue}.
     *
     * <p>Constraints like {@code @Min} look at one field in isolation. When a
     * rule spans several fields - "honours requires a grade of exactly 30" -
     * you need either a custom class-level constraint or, for a one-off rule
     * like this, an {@code @AssertTrue} method. Bean Validation treats any
     * {@code isXxx()} method as a read-only property and validates it.
     *
     * <p>The method must be a getter with no arguments. The property name
     * reported in the violation will be {@code honoursConsistent}.
     */
    @AssertTrue(message = "Honours (lode) requires a grade of exactly 30")
    @Transient
    public boolean isHonoursConsistent() {
        return !withHonours || (grade != null && grade == MAX_GRADE);
    }

    /** Formatted result, e.g. {@code "30 e lode"} or {@code "27"}. */
    @Transient
    public String formattedGrade() {
        if (grade == null) {
            return "-";
        }
        return withHonours ? grade + " e lode" : String.valueOf(grade);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Student getStudent() {
        return student;
    }

    /**
     * Package-private: only {@link Student#addEnrollment} and
     * {@link Student#removeEnrollment} may call it, keeping the two sides of the
     * association in sync.
     */
    void setStudent(Student student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getGrade() {
        return grade;
    }

    public boolean isWithHonours() {
        return withHonours;
    }

    @Override
    public String toString() {
        return "Enrollment{student=" + (student != null ? student.getStudentNumber() : null)
                + ", course=" + (course != null ? course.getCode() : null)
                + ", status=" + status
                + ", grade=" + formattedGrade() + "}";
    }
}
