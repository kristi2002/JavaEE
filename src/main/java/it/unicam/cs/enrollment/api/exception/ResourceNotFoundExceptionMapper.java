package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.exception.ResourceNotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link ResourceNotFoundException} into <b>404 Not Found</b>.
 *
 * <h2>What an {@code ExceptionMapper} is, and why it changes how you write code</h2>
 * Without mappers, every resource method must handle every failure:
 * <pre>
 *   try {
 *       return service.findById(id);
 *   } catch (ResourceNotFoundException e) {
 *       return Response.status(404).entity(...).build();
 *   } catch (BusinessRuleViolationException e) {
 *       return Response.status(409).entity(...).build();
 *   } ...
 * </pre>
 * That block gets copy-pasted into fifty endpoints and drifts in every one.
 *
 * <p>An {@code ExceptionMapper<E>} registers a single handler for a whole
 * exception type. Resource methods then describe only the successful path, and
 * error rendering happens in exactly one place per error KIND. This is the same
 * separation that {@code @ControllerAdvice} provides in Spring.
 *
 * <h2>How JAX-RS chooses a mapper</h2>
 * It picks the mapper whose type parameter is the CLOSEST SUPERTYPE of the
 * thrown exception. So {@code ResourceNotFoundException} lands here rather than
 * in the {@code Throwable} mapper, even though both would match. Specific
 * mappers automatically win.
 */
@Provider
public class ResourceNotFoundExceptionMapper implements ExceptionMapper<ResourceNotFoundException> {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceNotFoundExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ResourceNotFoundException exception) {
        // DEBUG, not ERROR. A 404 is a normal outcome of a normal API - somebody
        // followed a stale link. Logging it at ERROR would fill the log with
        // events nobody needs to act on, and train everyone to ignore ERROR.
        LOG.debug("Resource not found: {}", exception.getMessage());

        return ProblemDetails.build(
                Response.Status.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                exception.getMessage(),
                exception.getErrorCode(),
                uriInfo);
    }
}
