package it.unicam.cs.enrollment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A member of teaching staff who owns one or more {@link Course}s.
 *
 * <p>This entity is intentionally simpler than {@link Student}. It is here to
 * give {@code Course} something to point at, and so the codebase contains a
 * plain {@code @ManyToOne} target you can compare against the more elaborate
 * relationships elsewhere.
 *
 * <p>Note that it does NOT override {@code equals}/{@code hashCode}: it inherits
 * the id-based implementation from {@link BaseEntity}. It could reasonably use
 * {@code staffNumber} as a natural key, exactly as {@code Student} does - both
 * choices are defensible, and seeing both in one codebase is deliberate.
 */
@Entity
@Table(
        name = "professors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_professors_staff_number", columnNames = "staff_number"),
                @UniqueConstraint(name = "uk_professors_email", columnNames = "email")
        }
)
@NamedQuery(
        name = Professor.FIND_BY_STAFF_NUMBER,
        query = "SELECT p FROM Professor p WHERE p.staffNumber = :staffNumber"
)
@NamedQuery(
        name = Professor.FIND_ALL_ORDERED,
        query = "SELECT p FROM Professor p ORDER BY p.lastName ASC, p.firstName ASC"
)
public class Professor extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public static final String FIND_BY_STAFF_NUMBER = "Professor.findByStaffNumber";
    public static final String FIND_ALL_ORDERED = "Professor.findAllOrdered";

    @NotBlank
    @Size(max = 10)
    @Column(name = "staff_number", nullable = false, length = 10, updatable = false)
    private String staffNumber;

    @NotBlank
    @Size(max = 80)
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @NotBlank
    @Size(max = 80)
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Valid
    @Embedded
    private Email email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "title", nullable = false, length = 30)
    private AcademicTitle title;

    @NotBlank
    @Size(max = 120)
    @Column(name = "department", nullable = false, length = 120)
    private String department;

    /**
     * The inverse side of {@code Course.professor}.
     *
     * <p>No cascade here, unlike {@code Student.enrollments}. Deleting a
     * professor must NOT delete their courses - the courses belong to the
     * department, not to the person. Cascade is a statement about ownership;
     * apply it only when the child genuinely cannot exist alone.
     */
    @OneToMany(mappedBy = "professor", fetch = FetchType.LAZY)
    private Set<Course> courses = new LinkedHashSet<>();

    protected Professor() {
        // required by JPA
    }

    public Professor(String staffNumber, String firstName, String lastName,
                     Email email, AcademicTitle title, String department) {
        this.staffNumber = staffNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.title = title;
        this.department = department;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** Total teaching load in credits across all owned courses. */
    public int teachingLoad() {
        return courses.stream().mapToInt(Course::getCredits).sum();
    }

    public String getStaffNumber() {
        return staffNumber;
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

    public AcademicTitle getTitle() {
        return title;
    }

    public void setTitle(AcademicTitle title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Set<Course> getCourses() {
        return Collections.unmodifiableSet(courses);
    }

    @Override
    public String toString() {
        return "Professor{" + staffNumber + ", " + fullName() + "}";
    }
}
