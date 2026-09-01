package it.unicam.cs.enrollment.exception;

/**
 * The requested entity does not exist. Maps to HTTP <b>404 Not Found</b>.
 *
 * <p>The static factory methods exist so that error messages are CONSISTENT.
 * Left to free-form construction, the same condition ends up reported as
 * "Student 5 not found", "no such student: 5" and "Student[5] missing" in three
 * different places, and none of them can be searched for reliably in a log
 * aggregator.
 *
 * <p>Note what the message does <i>not</i> contain: no SQL, no stack detail, no
 * internal identifiers beyond the one the caller supplied. Error messages cross
 * a trust boundary and are a real source of information disclosure.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    /** e.g. {@code ResourceNotFoundException.of("Student", 42L)}. */
    public static ResourceNotFoundException of(String resourceType, Object identifier) {
        return new ResourceNotFoundException(
                resourceType + " with identifier '" + identifier + "' was not found");
    }

    /** e.g. {@code ResourceNotFoundException.of("Course", "code", "CS101")}. */
    public static ResourceNotFoundException of(String resourceType, String field, Object value) {
        return new ResourceNotFoundException(
                resourceType + " with " + field + " '" + value + "' was not found");
    }
}
