package it.unicam.cs.enrollment.domain.model;

import it.unicam.cs.enrollment.domain.validation.StudentNumber;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A person enrolled in a degree programme.
 *
 * <h2>Anatomy of a JPA entity</h2>
 * <ul>
 *   <li>{@code @Entity} - this class is mapped to a table and can be queried.</li>
 *   <li>{@code @Table} - the physical mapping. Naming the constraints and
 *       indexes explicitly matters: an auto-generated name like
 *       {@code UK_a8f3d} tells you nothing when it shows up in a production
 *       error log at 3am.</li>
 *   <li>{@code @NamedQuery} - a JPQL query parsed and validated once at
 *       DEPLOY time rather than on every call. A typo therefore fails the
 *       deployment instead of a user request. Prefer these over strings
 *       scattered through repository code.</li>
 * </ul>
 */
@Entity
@Table(
        name = "students",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_students_student_number", columnNames = "student_number"),
                @UniqueConstraint(name = "uk_students_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_students_last_name", columnList = "last_name"),
                @Index(name = "idx_students_status", columnList = "status")
        }
)
@NamedQuery(
        name = Student.FIND_BY_STUDENT_NUMBER,
        query = "SELECT s FROM Student s WHERE s.studentNumber = :studentNumber"
)
@NamedQuery(
        name = Student.COUNT_BY_STATUS,
        query = "SELECT COUNT(s) FROM Student s WHERE s.status = :status"
)
public class Student extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * Named-query identifiers as constants.
     *
     * <p>Why constants instead of typing the string at the call site? Because
     * the compiler cannot check a string literal. With a constant, renaming the
     * query is a refactor your IDE performs safely, and "find usages" works.
     * This is a small habit that separates tidy codebases from fragile ones.
     */
    public static final String FIND_BY_STUDENT_NUMBER = "Student.findByStudentNumber";
    public static final String COUNT_BY_STATUS = "Student.countByStatus";

    /**
     * The NATURAL KEY: the university-issued matricola. Unique, immutable, and
     * meaningful to humans - the opposite of the surrogate {@code id}.
     *
     * <p>{@code @StudentNumber} is a CUSTOM Bean Validation constraint defined
     * in this project. Writing your own constraint (rather than repeating a
     * {@code @Pattern} regex in fifteen places) means the rule has one home and
     * one error message.
     */
    @StudentNumber
    @Column(name = "student_number", nullable = false, length = 10, updatable = false)
    private String studentNumber;

    @NotBlank
    @Size(max = 80)
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @NotBlank
    @Size(max = 80)
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    /**
     * {@code @Embedded} pulls the {@link Email} value object's columns into this
     * table. {@code @Valid} is what makes Bean Validation CASCADE into the
     * embeddable - without it, the constraints declared inside {@code Email}
     * would silently never run. Forgetting {@code @Valid} on nested objects is
     * one of the most common validation bugs in real projects.
     */
    @Valid
    @Embedded
    private Email email;

    /**
     * {@code @Past} is a temporal constraint: the value must be strictly before
     * "now" at the moment validation runs. Siblings worth knowing:
     * {@code @PastOrPresent}, {@code @Future}, {@code @FutureOrPresent}.
     */
    @NotNull
    @Past
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * ALWAYS use {@code EnumType.STRING}, never the default {@code ORDINAL}.
     *
     * <p>{@code ORDINAL} stores the enum's position (0, 1, 2...). The day
     * somebody inserts a new constant in the middle of the enum, every existing
     * row silently changes meaning. It is a genuinely dangerous default and
     * costs nothing to avoid. {@code STRING} stores {@code 'ACTIVE'}, which is
     * also readable when you query the table by hand.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "enrollment_year", nullable = false)
    private int enrollmentYear;

    /**
     * ONE-TO-MANY, the "inverse" (non-owning) side.
     *
     * <p>Four things are happening here, and each is a decision:
     * <ol>
     *   <li>{@code mappedBy = "student"} - the {@code Enrollment.student} field
     *       OWNS the foreign key. The owning side is always the one holding the
     *       FK column; the inverse side is just a convenient view. If you set
     *       only the inverse side, nothing is written to the database.</li>
     *   <li>{@code fetch = LAZY} - the collection is a placeholder until first
     *       touched. Collections are lazy by default and you should keep them
     *       that way; an EAGER collection means every {@code find()} drags the
     *       whole object graph into memory.</li>
     *   <li>{@code cascade = ALL} - persist/merge/remove propagate to the
     *       children. Correct here because an enrollment cannot exist without
     *       its student: student is the AGGREGATE ROOT.</li>
     *   <li>{@code orphanRemoval = true} - removing an element from this set
     *       DELETEs the row. This is what makes {@link #removeEnrollment}
     *       actually work.</li>
     * </ol>
     */
    @OneToMany(
            mappedBy = "student",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private Set<Enrollment> enrollments = new LinkedHashSet<>();

    /** Required by JPA. */
    protected Student() {
        // required by JPA
    }

    public Student(String studentNumber, String firstName, String lastName,
                   Email email, LocalDate dateOfBirth, int enrollmentYear) {
        this.studentNumber = studentNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.enrollmentYear = enrollmentYear;
        this.status = StudentStatus.ACTIVE;
    }

    // ------------------------------------------------------------------
    // Domain behaviour
    //
    // An entity is not a bag of getters and setters ("anemic domain model").
    // Rules that are always true about a Student belong HERE, where they
    // cannot be bypassed, rather than in whichever service happens to
    // remember them.
    // ------------------------------------------------------------------

    /**
     * BIDIRECTIONAL ASSOCIATION HELPER.
     *
     * <p>Java does not keep the two sides of a relationship in sync for you. If
     * you add an Enrollment to this set but never set {@code enrollment.student},
     * the in-memory graph and the database disagree - the classic symptom is
     * "my child row has a null foreign key" or "the collection is empty until I
     * restart".
     *
     * <p>The fix is to funnel every mutation through a helper that updates both
     * sides. Make the collection getter unmodifiable (below) so nobody can skip it.
     */
    public void addEnrollment(Enrollment enrollment) {
        Objects.requireNonNull(enrollment, "enrollment must not be null");
        this.enrollments.add(enrollment);
        enrollment.setStudent(this);
    }

    public void removeEnrollment(Enrollment enrollment) {
        Objects.requireNonNull(enrollment, "enrollment must not be null");
        this.enrollments.remove(enrollment);
        enrollment.setStudent(null);
    }

    /** @return whether university rules allow this student to take a new course. */
    public boolean canEnroll() {
        return status.canEnroll();
    }

    /** Total ECTS/CFU credits earned from successfully completed courses. */
    public int earnedCredits() {
        return enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED)
                .mapToInt(e -> e.getCourse().getCredits())
                .sum();
    }

    /**
     * Weighted grade average, the number Italian students actually care about.
     * Weighted by credits, as the university computes it.
     *
     * <p>Returns 0.0 when nothing has been completed - deliberately, rather than
     * dividing by zero.
     */
    public double weightedAverage() {
        int totalCredits = 0;
        int weightedSum = 0;
        for (Enrollment e : enrollments) {
            if (e.getStatus() == EnrollmentStatus.COMPLETED && e.getGrade() != null) {
                int credits = e.getCourse().getCredits();
                totalCredits += credits;
                weightedSum += e.getGrade() * credits;
            }
        }
        return totalCredits == 0 ? 0.0 : (double) weightedSum / totalCredits;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public void suspend() {
        this.status = StudentStatus.SUSPENDED;
    }

    public void reinstate() {
        if (status == StudentStatus.SUSPENDED) {
            this.status = StudentStatus.ACTIVE;
        }
    }

    public void graduate() {
        this.status = StudentStatus.GRADUATED;
    }

    // ------------------------------------------------------------------
    // Accessors
    //
    // Note which setters DO NOT exist: studentNumber and enrollmentYear are
    // immutable after creation. Only expose a setter when the field is
    // genuinely allowed to change - "every field gets a setter" is a habit
    // from tooling, not a design principle.
    // ------------------------------------------------------------------

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public void setStatus(StudentStatus status) {
        this.status = status;
    }

    public int getEnrollmentYear() {
        return enrollmentYear;
    }

    /**
     * DEFENSIVE COPY / unmodifiable view. Callers can read the collection but
     * cannot bypass {@link #addEnrollment}. Handing out your internal mutable
     * state is called "leaking the representation" and it defeats every
     * invariant the class tries to maintain.
     */
    public Set<Enrollment> getEnrollments() {
        return Collections.unmodifiableSet(enrollments);
    }

    /**
     * Equality by NATURAL KEY, overriding the id-based version in
     * {@link BaseEntity}. This is strictly better when a stable business key
     * exists: two Student objects representing matricola 123456 are equal even
     * before either has been persisted and given an id.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Student)) {
            return false;
        }
        return studentNumber != null
                && studentNumber.equals(((Student) other).getStudentNumber());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(studentNumber);
    }

    @Override
    public String toString() {
        return "Student{" + studentNumber + ", " + fullName() + ", " + status + "}";
    }
}
