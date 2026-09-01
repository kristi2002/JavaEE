package it.unicam.cs.enrollment.api.dto.request;

import it.unicam.cs.enrollment.domain.model.Semester;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Body for {@code POST /api/courses}.
 *
 * <p>Note that {@code semester} is typed as the {@link Semester} ENUM rather
 * than a String. JSON-B maps {@code "FALL"} onto the constant and rejects
 * anything else with a 400 before your code runs - free validation, and the
 * legal values are self-documenting. Accepting a raw String here would mean
 * writing that check by hand and getting a worse error message.
 */
public class CreateCourseRequest {

    /**
     * {@code @Pattern} with an explicit, anchored regex. Anchoring with
     * {@code ^...$} matters: without it, {@code "XXCS101YY"} would pass, because
     * the constraint only requires a match SOMEWHERE in the string.
     */
    @NotBlank(message = "code is required")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{3}$",
            message = "code must be two uppercase letters followed by three digits, e.g. CS101")
    private String code;

    @NotBlank(message = "title is required")
    @Size(max = 160)
    private String title;

    @Size(max = 2000)
    private String description;

    @Min(value = 1, message = "credits must be at least {value}")
    @Max(value = 30, message = "credits must be at most {value}")
    private int credits;

    @Min(value = 1, message = "capacity must be at least {value}")
    @Max(value = 1000, message = "capacity must be at most {value}")
    private int capacity;

    @NotNull(message = "semester is required (FALL or SPRING)")
    private Semester semester;

    @Min(2000)
    @Max(2100)
    private int academicYear;

    @NotNull(message = "professorId is required")
    private Long professorId;

    @NotNull(message = "enrollmentOpensAt is required (ISO-8601, e.g. 2026-09-01T00:00:00Z)")
    private Instant enrollmentOpensAt;

    @NotNull(message = "enrollmentClosesAt is required (ISO-8601)")
    private Instant enrollmentClosesAt;

    /**
     * Optional. A {@code null} list and an empty list mean the same thing here,
     * and the command object normalises them so downstream code never has to
     * null-check. Collapsing "absent" and "empty" early is a small kindness to
     * every method that follows.
     */
    private List<Long> prerequisiteCourseIds;

    public CreateCourseRequest() {
        // required by JSON-B
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Semester getSemester() {
        return semester;
    }

    public void setSemester(Semester semester) {
        this.semester = semester;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(int academicYear) {
        this.academicYear = academicYear;
    }

    public Long getProfessorId() {
        return professorId;
    }

    public void setProfessorId(Long professorId) {
        this.professorId = professorId;
    }

    public Instant getEnrollmentOpensAt() {
        return enrollmentOpensAt;
    }

    public void setEnrollmentOpensAt(Instant enrollmentOpensAt) {
        this.enrollmentOpensAt = enrollmentOpensAt;
    }

    public Instant getEnrollmentClosesAt() {
        return enrollmentClosesAt;
    }

    public void setEnrollmentClosesAt(Instant enrollmentClosesAt) {
        this.enrollmentClosesAt = enrollmentClosesAt;
    }

    public List<Long> getPrerequisiteCourseIds() {
        return prerequisiteCourseIds;
    }

    public void setPrerequisiteCourseIds(List<Long> prerequisiteCourseIds) {
        this.prerequisiteCourseIds = prerequisiteCourseIds;
    }
}
