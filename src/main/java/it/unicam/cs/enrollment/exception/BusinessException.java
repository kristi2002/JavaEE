package it.unicam.cs.enrollment.exception;

/**
 * Base type for every error this application raises deliberately.
 *
 * <h2>Checked or unchecked?</h2>
 * This extends {@link RuntimeException} (unchecked), which is the near-universal
 * choice in modern enterprise Java. Two reasons:
 * <ol>
 *   <li><b>Transactions.</b> In JTA, an unchecked exception thrown out of a
 *       {@code @Transactional} method rolls the transaction back automatically.
 *       A CHECKED exception does NOT - the container assumes you handled it and
 *       COMMITS. That default has silently corrupted a lot of data over the
 *       years. If you must throw checked exceptions, mark them explicitly with
 *       {@code @Transactional(rollbackOn = ...)}.</li>
 *   <li><b>Signal-to-noise.</b> Checked exceptions force every intermediate
 *       layer to declare or wrap errors it cannot do anything about, which in
 *       practice produces empty {@code catch} blocks.</li>
 * </ol>
 *
 * <h2>Why a machine-readable error code</h2>
 * A message is for a human; a code is for a client. Front-end code that
 * branches on {@code "COURSE_FULL"} keeps working when the wording is
 * translated or reworded, whereas code that string-matches the message breaks.
 * The code is what we expose in the {@code type}/{@code code} field of the
 * RFC 7807 error body.
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable, machine-readable identifier, e.g. {@code COURSE_FULL}. */
    private final String errorCode;

    protected BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Always offer a constructor that takes a {@code cause}. Losing the original
     * stack trace when wrapping an exception is one of the most frustrating
     * things you can do to whoever debugs the incident.
     */
    protected BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
