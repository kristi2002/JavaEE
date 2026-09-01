package it.unicam.cs.enrollment.api.dto.response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A standard error body, following RFC 7807 "Problem Details for HTTP APIs".
 *
 * <h2>Why follow a standard instead of inventing an error shape</h2>
 * Every API invents its own error JSON, and every client then writes bespoke
 * parsing for each API it talks to. RFC 7807 is the agreed answer, and the
 * ecosystem knows it: Spring ships {@code ProblemDetail}, Quarkus and Micronaut
 * support it, and API gateways can interpret it.
 *
 * <p>The standard fields:
 * <ul>
 *   <li>{@code type}   - a URI identifying the problem KIND. Dereferenceable in
 *       principle; in practice usually a stable string clients switch on.</li>
 *   <li>{@code title}  - short, human-readable summary. Same for every
 *       occurrence of this type.</li>
 *   <li>{@code status} - the HTTP status code, repeated in the body so it
 *       survives logging and proxying.</li>
 *   <li>{@code detail} - human-readable explanation OF THIS occurrence.</li>
 *   <li>{@code instance} - URI of the specific request that failed.</li>
 * </ul>
 * Extensions are allowed, and we add three that matter operationally:
 * {@code errorCode}, {@code correlationId} and {@code violations}.
 *
 * <h2>{@code correlationId} is the field you will be most grateful for</h2>
 * When a user reports "it failed at 14:32", a correlation id turns an
 * archaeology session into one log query. Return it in the error body AND in a
 * response header, and log it on every line of the request.
 *
 * <h2>What must NEVER go in here</h2>
 * Stack traces, SQL, class names, file paths, connection strings. Error
 * responses cross a trust boundary; everything in them is public. Log the
 * details server-side and give the client the correlation id that points at them.
 */
public class ProblemDetail {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;

    /** Extension: our stable machine-readable code, e.g. {@code COURSE_FULL}. */
    private String errorCode;

    /** Extension: ties this response to the server-side log entries. */
    private String correlationId;

    private Instant timestamp;

    /** Extension: per-field messages, populated only for validation failures. */
    private List<Violation> violations;

    public ProblemDetail() {
        this.timestamp = Instant.now();
    }

    public ProblemDetail(String type, String title, int status, String detail) {
        this();
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
    }

    /**
     * One field-level validation failure.
     *
     * <p>A NESTED STATIC class. Static matters: a non-static inner class holds a
     * hidden reference to its enclosing instance, which serialisation frameworks
     * handle badly and which leaks memory when instances outlive their parent.
     * Nest a helper type only when it is meaningless outside its parent - as
     * this one is.
     */
    public static class Violation {

        private String field;
        private String message;
        private Object rejectedValue;

        public Violation() {
            // required by JSON-B
        }

        public Violation(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getRejectedValue() {
            return rejectedValue;
        }

        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }
    }

    public void addViolation(String field, String message, Object rejectedValue) {
        if (violations == null) {
            violations = new ArrayList<>();
        }
        violations.add(new Violation(field, message, rejectedValue));
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public void setViolations(List<Violation> violations) {
        this.violations = violations;
    }
}
