package it.unicam.cs.enrollment.exception;

/**
 * A request was well-formed but breaks a domain rule. Maps to HTTP
 * <b>409 Conflict</b> (or 422 Unprocessable Entity, depending on house style).
 *
 * <h2>The distinction that matters</h2>
 * <ul>
 *   <li><b>400 Bad Request</b> - the input is malformed. "credits": "abc".
 *       Caught by Bean Validation before your code runs.</li>
 *   <li><b>409 Conflict</b> - the input is perfectly valid, but the current
 *       state of the system forbids it. The course is full; the enrollment
 *       window closed. Only your business logic can know this.</li>
 * </ul>
 * Getting this right is what makes an API pleasant to integrate against: a 400
 * tells the client "fix your request", a 409 tells it "your request was fine,
 * try again later or choose something else".
 *
 * <p>The nested constants below give each rule a stable code. Defining them as
 * factory methods on the exception keeps message and code together, so they can
 * never drift apart.
 */
public class BusinessRuleViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    // ------------------------------------------------------------------
    // One factory per rule. Adding a rule here rather than throwing an
    // ad-hoc exception at the call site means the full catalogue of things
    // that can go wrong is discoverable in one file.
    // ------------------------------------------------------------------

    public static BusinessRuleViolationException courseFull(String courseCode, int capacity) {
        return new BusinessRuleViolationException(
                "COURSE_FULL",
                "Course " + courseCode + " has reached its capacity of " + capacity + " students");
    }

    public static BusinessRuleViolationException enrollmentWindowClosed(String courseCode) {
        return new BusinessRuleViolationException(
                "ENROLLMENT_WINDOW_CLOSED",
                "The enrollment window for course " + courseCode + " is not currently open");
    }

    public static BusinessRuleViolationException studentNotEligible(String studentNumber, String status) {
        return new BusinessRuleViolationException(
                "STUDENT_NOT_ELIGIBLE",
                "Student " + studentNumber + " cannot enroll while in status " + status);
    }

    public static BusinessRuleViolationException prerequisitesNotMet(String courseCode,
                                                                    String missingCourses) {
        return new BusinessRuleViolationException(
                "PREREQUISITES_NOT_MET",
                "Cannot enroll in " + courseCode
                        + ": the following prerequisites have not been passed: " + missingCourses);
    }

    public static BusinessRuleViolationException illegalStateTransition(String detail) {
        return new BusinessRuleViolationException("ILLEGAL_STATE_TRANSITION", detail);
    }

    public static BusinessRuleViolationException invalidGrade(String detail) {
        return new BusinessRuleViolationException("INVALID_GRADE", detail);
    }
}
