package it.unicam.cs.enrollment.api.dto.response;

import java.time.Instant;

/**
 * What the API returns for an enrollment.
 *
 * <p>Both sides of the association are flattened into scalar fields
 * ({@code studentNumber}, {@code courseCode}) instead of nesting the two
 * entities. Nesting would make {@code GET /students/{id}} return the student,
 * their enrollments, each enrollment's course, and each course's professor - the
 * payload balloons, and every extra level is another thing you can never remove
 * without breaking a client.
 *
 * <p>{@link #formattedGrade} is the presentation-ready string ("30 e lode"),
 * sitting alongside the raw {@code grade} and {@code withHonours}. Sending both
 * lets simple clients display the string and sophisticated ones compute with the
 * numbers, without either having to reimplement Italian grading conventions.
 */
public class EnrollmentResponse {

    private Long id;

    private Long studentId;
    private String studentNumber;
    private String studentName;

    private Long courseId;
    private String courseCode;
    private String courseTitle;
    private int courseCredits;

    private String status;
    private Instant enrolledAt;
    private Instant completedAt;

    private Integer grade;
    private boolean withHonours;
    private String formattedGrade;

    public EnrollmentResponse() {
        // required by JSON-B
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public int getCourseCredits() {
        return courseCredits;
    }

    public void setCourseCredits(int courseCredits) {
        this.courseCredits = courseCredits;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public boolean isWithHonours() {
        return withHonours;
    }

    public void setWithHonours(boolean withHonours) {
        this.withHonours = withHonours;
    }

    public String getFormattedGrade() {
        return formattedGrade;
    }

    public void setFormattedGrade(String formattedGrade) {
        this.formattedGrade = formattedGrade;
    }
}
