package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.api.dto.response.ProblemDetail;
import it.unicam.cs.enrollment.api.filter.CorrelationIdFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.MDC;

/**
 * Shared construction of RFC 7807 error responses.
 *
 * <p>A {@code final} class with a private constructor and only static methods -
 * the standard shape for a UTILITY CLASS. The private constructor is not
 * pedantry: without it the compiler generates a public one, and someone will
 * eventually instantiate a class that has no state.
 *
 * <p>Why this exists at all: six exception mappers each need to build the same
 * envelope, attach the correlation id, and set the content type. Duplicating
 * that six times guarantees the sixth copy eventually differs from the others.
 */
final class ProblemDetails {

    /**
     * The base URI for problem types. RFC 7807 says {@code type} should be a URI
     * that documents the error. In a real deployment these would resolve to a
     * page in your API documentation.
     */
    private static final String TYPE_BASE = "https://api.unicam.it/problems/";

    private ProblemDetails() {
        throw new AssertionError("utility class - do not instantiate");
    }

    /**
     * Builds the body and wraps it in a {@link Response}.
     *
     * @param status    the HTTP status
     * @param typeSlug  short kebab-case identifier appended to {@link #TYPE_BASE}
     * @param title     constant, human-readable summary of this KIND of problem
     * @param detail    explanation of THIS occurrence
     * @param errorCode our machine-readable code, may be {@code null}
     * @param uriInfo   used to fill {@code instance}; may be {@code null}
     */
    static Response build(Response.Status status,
                          String typeSlug,
                          String title,
                          String detail,
                          String errorCode,
                          UriInfo uriInfo) {

        ProblemDetail problem = new ProblemDetail(
                TYPE_BASE + typeSlug, title, status.getStatusCode(), detail);

        problem.setErrorCode(errorCode);

        if (uriInfo != null) {
            problem.setInstance(uriInfo.getPath());
        }

        // Read the id the filter put in the MDC, so the client receives the same
        // identifier that appears on every server-side log line for this request.
        problem.setCorrelationId(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        return Response.status(status)
                // The media type registered for RFC 7807. Clients that
                // understand it can parse the body generically; those that do
                // not still see ordinary JSON.
                .type(MediaType.APPLICATION_JSON)
                .entity(problem)
                .build();
    }

    /** Variant that lets the caller enrich the body before it is wrapped. */
    static ProblemDetail create(Response.Status status,
                                String typeSlug,
                                String title,
                                String detail,
                                String errorCode,
                                UriInfo uriInfo) {

        ProblemDetail problem = new ProblemDetail(
                TYPE_BASE + typeSlug, title, status.getStatusCode(), detail);
        problem.setErrorCode(errorCode);
        if (uriInfo != null) {
            problem.setInstance(uriInfo.getPath());
        }
        problem.setCorrelationId(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        return problem;
    }
}
