package it.unicam.cs.enrollment.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body for {@code POST /api/enrollments}.
 *
 * <p>{@code @Positive} rather than {@code @Min(1)}: both reject 0 and negatives,
 * but the name states the intent. Prefer the constraint whose NAME describes the
 * rule - the violation message a client receives comes from that name too.
 */
public class EnrollRequest {

    @NotNull(message = "studentId is required")
    @Positive(message = "studentId must be a positive number")
    private Long studentId;

    @NotNull(message = "courseId is required")
    @Positive(message = "courseId must be a positive number")
    private Long courseId;

    public EnrollRequest() {
        // required by JSON-B
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
