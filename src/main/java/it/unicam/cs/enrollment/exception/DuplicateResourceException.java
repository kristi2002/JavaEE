package it.unicam.cs.enrollment.exception;

/**
 * An entity that must be unique already exists. Maps to HTTP <b>409 Conflict</b>.
 *
 * <p>Worth separating from {@link BusinessRuleViolationException} because
 * clients frequently want to handle it specially - "this matricola is already
 * registered, did you mean to log in?" is a different user experience from
 * "the course is full".
 *
 * <p><b>A note on how duplicates are actually detected.</b> Checking
 * "does it exist?" and then inserting is a TIME-OF-CHECK-TO-TIME-OF-USE race:
 * two concurrent requests can both pass the check before either inserts. The
 * check is still worth doing because it produces a good error message in the
 * common case, but the guarantee comes from the UNIQUE constraint in the
 * database. The application converts the resulting integrity violation into
 * this exception.
 */
public class DuplicateResourceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String ERROR_CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(ERROR_CODE, message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    public static DuplicateResourceException of(String resourceType, String field, Object value) {
        return new DuplicateResourceException(
                resourceType + " with " + field + " '" + value + "' already exists");
    }
}
