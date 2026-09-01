package it.unicam.cs.enrollment.exception;

/**
 * The request itself is malformed. Maps to HTTP <b>400 Bad Request</b>.
 *
 * <h2>Why this exists separately from {@link BusinessRuleViolationException}</h2>
 * This project states a rule in its own documentation:
 * <ul>
 *   <li><b>400</b> — the input is malformed. "Fix your request."</li>
 *   <li><b>409</b> — the input is valid, but the current state forbids it.
 *       "Your request was fine; try later or choose something else."</li>
 * </ul>
 *
 * <p>An unrecognised value for {@code ?status=} is squarely the first case:
 * {@code BANANA} is not a status and never will be, regardless of what is in the
 * database. Reporting it as a 409 would tell the client to retry something that
 * can never succeed.
 *
 * <p>Most such input is caught by Bean Validation before any of our code runs.
 * This exception covers what is left: values we parse by hand, because letting
 * JAX-RS convert a {@code @QueryParam} directly into an enum makes it return
 * <b>404</b> on a bad value — a genuinely confusing answer, since the collection
 * being queried exists perfectly well.
 *
 * <p>Following your own documented conventions is worth more than any single
 * convention. An API that is 90% consistent forces clients to special-case the
 * other 10% forever.
 */
public class InvalidRequestException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Rejects an unparseable enum value, listing the legal ones.
     *
     * <p>Naming the valid values in the message matters. "Invalid status" makes
     * the caller go and read your documentation; "Valid values: ACTIVE,
     * SUSPENDED, GRADUATED, WITHDRAWN" lets them fix it immediately. Error
     * messages are a user interface.
     */
    public static InvalidRequestException invalidEnumValue(String parameter,
                                                           String supplied,
                                                           Class<? extends Enum<?>> enumType) {
        StringBuilder valid = new StringBuilder();
        for (Enum<?> constant : enumType.getEnumConstants()) {
            if (valid.length() > 0) {
                valid.append(", ");
            }
            valid.append(constant.name());
        }
        return new InvalidRequestException(
                "INVALID_PARAMETER",
                "Unknown value '" + supplied + "' for parameter '" + parameter
                        + "'. Valid values: " + valid);
    }
}
