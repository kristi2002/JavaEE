package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.exception.InvalidRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link InvalidRequestException} into <b>400 Bad Request</b>.
 *
 * <p>Sits alongside {@code ConstraintViolationExceptionMapper}, which handles
 * the same status for failures Bean Validation detected automatically. Two
 * mappers, one status, different sources — and both produce the same RFC 7807
 * shape, so a client cannot tell (or care) which path a 400 came from.
 */
@Provider
public class InvalidRequestExceptionMapper implements ExceptionMapper<InvalidRequestException> {

    private static final Logger LOG = LoggerFactory.getLogger(InvalidRequestExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(InvalidRequestException exception) {
        LOG.debug("Invalid request: {}", exception.getMessage());

        return ProblemDetails.build(
                Response.Status.BAD_REQUEST,
                "invalid-request",
                "Invalid Request",
                exception.getMessage(),
                exception.getErrorCode(),
                uriInfo);
    }
}
