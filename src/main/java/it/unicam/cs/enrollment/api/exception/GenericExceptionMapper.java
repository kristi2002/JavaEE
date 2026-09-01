package it.unicam.cs.enrollment.api.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CATCH-ALL mapper: anything not handled by a more specific mapper becomes
 * <b>500 Internal Server Error</b> with a safe, generic body.
 *
 * <h2>Why a catch-all is mandatory</h2>
 * Without one, an unexpected exception reaches the container's own error page,
 * which in many default configurations includes the STACK TRACE. That leaks your
 * package structure, library versions and sometimes SQL or file paths - a real
 * information-disclosure finding in any security review.
 *
 * <p>The contract this class implements:
 * <ul>
 *   <li><b>Log everything server-side</b>, at ERROR, WITH the stack trace. This
 *       is the one place a full stack trace belongs.</li>
 *   <li><b>Return almost nothing to the client</b> - a generic message plus the
 *       correlation id that ties it to the log entry.</li>
 * </ul>
 * "Log the detail, return the reference" is the rule for every unexpected error.
 *
 * <h2>Why {@code WebApplicationException} is passed through</h2>
 * JAX-RS itself throws these for ordinary HTTP conditions: {@code NotFoundException}
 * (404) when no resource matches a URI, {@code NotAllowedException} (405),
 * {@code NotSupportedException} (415). They already carry the right status, so
 * turning them into 500s would be actively wrong - a mistyped URL would report a
 * server crash.
 *
 * <h2>Mapper precedence</h2>
 * This mapper is declared for {@code Throwable}, the least specific type
 * possible, so JAX-RS only reaches it when nothing else matches. That is exactly
 * the intent: it is the safety net, never the first choice.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(GenericExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {

        // ---- Pass through framework exceptions that already know their status
        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            Response original = wae.getResponse();
            Response.Status status = Response.Status.fromStatusCode(original.getStatus());

            if (status == null) {
                status = Response.Status.INTERNAL_SERVER_ERROR;
            }

            LOG.debug("WebApplicationException {}: {}", original.getStatus(), exception.getMessage());

            return ProblemDetails.build(
                    status,
                    "http-error",
                    status.getReasonPhrase(),
                    // The message from a framework exception is safe to expose:
                    // it says things like "RESTEASY003210: Could not find resource".
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : status.getReasonPhrase(),
                    "HTTP_" + original.getStatus(),
                    uriInfo);
        }

        // ---- Genuinely unexpected: log loudly, say little
        //
        // Passing the exception as the LAST argument to an SLF4J method logs the
        // full stack trace. Note there is no {} placeholder for it - that is the
        // API's convention for a trailing Throwable, and getting it wrong (using
        // e.getMessage() instead) is how stack traces go missing.
        LOG.error("Unhandled exception while processing {}",
                uriInfo != null ? uriInfo.getPath() : "unknown path", exception);

        return ProblemDetails.build(
                Response.Status.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal Server Error",
                // Deliberately vague. Everything useful is in the log, findable
                // by the correlation id that this body carries.
                "An unexpected error occurred. Please quote the correlation id "
                        + "when reporting this problem.",
                "INTERNAL_ERROR",
                uriInfo);
    }
}
