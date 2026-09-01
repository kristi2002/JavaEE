package it.unicam.cs.enrollment.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code POST /api/enrollments/{id}/grade}.
 *
 * <p>The cross-field rule - honours requires exactly 30 - is NOT checked here.
 * It lives on the {@code Enrollment} entity, and deliberately so: a rule about
 * the meaning of a grade is domain knowledge, and putting it in a DTO would tie
 * it to one HTTP endpoint. Anything else that records a grade (an import, an
 * admin tool) would then be free to break it.
 *
 * <p>The division of labour is worth memorising:
 * <ul>
 *   <li><b>DTO validation</b> - is the input SHAPE plausible? Field present,
 *       number in range, string not too long. Produces 400.</li>
 *   <li><b>Domain validation</b> - is this operation MEANINGFUL given the rules
 *       and the current state? Produces 409.</li>
 * </ul>
 */
public class RecordGradeRequest {

    @NotNull(message = "grade is required")
    @Min(value = 18, message = "a passing grade is at least {value}")
    @Max(value = 30, message = "the maximum grade is {value}")
    private Integer grade;

    /**
     * Primitive {@code boolean}, so an omitted field defaults to {@code false}.
     * That is the correct default for an award you must explicitly grant. Using
     * {@code Boolean} here would let {@code null} reach the domain and force a
     * null check for no benefit.
     */
    private boolean withHonours;

    public RecordGradeRequest() {
        // required by JSON-B
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
}
