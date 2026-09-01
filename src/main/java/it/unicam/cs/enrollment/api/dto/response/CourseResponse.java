package it.unicam.cs.enrollment.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * What the API returns for a course.
 *
 * <p>{@link #professorName} is FLATTENED from the associated Professor entity
 * rather than nested as an object. That is a deliberate API design choice: a
 * course listing needs a name to display, not the professor's full record.
 * Flattening keeps the payload small and avoids exposing a second entity's shape
 * through this endpoint. When a client genuinely needs the professor, it follows
 * {@code professorId} to {@code /api/professors/{id}}.
 */
public class CourseResponse {

    private Long id;
    private String code;
    private String title;
    private String description;
    private int credits;
    private int capacity;

    /**
     * Computed with a COUNT query at request time. Clients care about this far
     * more than about {@code capacity} - it answers "can I still sign up?".
     */
    private long availableSeats;

    private String semester;
    private int academicYear;

    private Long professorId;
    private String professorName;

    private Instant enrollmentOpensAt;
    private Instant enrollmentClosesAt;

    /** Derived server-side so clients do not each re-implement the window rule. */
    private boolean enrollmentOpen;

    /** Course codes only - enough for a client to display or look up. */
    private List<String> prerequisiteCodes;

    public CourseResponse() {
        // required by JSON-B
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public long getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(long availableSeats) {
        this.availableSeats = availableSeats;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
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

    public String getProfessorName() {
        return professorName;
    }

    public void setProfessorName(String professorName) {
        this.professorName = professorName;
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

    public boolean isEnrollmentOpen() {
        return enrollmentOpen;
    }

    public void setEnrollmentOpen(boolean enrollmentOpen) {
        this.enrollmentOpen = enrollmentOpen;
    }

    public List<String> getPrerequisiteCodes() {
        return prerequisiteCodes;
    }

    public void setPrerequisiteCodes(List<String> prerequisiteCodes) {
        this.prerequisiteCodes = prerequisiteCodes;
    }
}
