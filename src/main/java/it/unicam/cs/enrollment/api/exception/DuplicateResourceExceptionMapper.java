package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.exception.DuplicateResourceException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link DuplicateResourceException} into <b>409 Conflict</b>.
 *
 * <p>Same status as a business-rule violation, but a distinct {@code errorCode}
 * and {@code type}. That is the point of RFC 7807's {@code type} field: several
 * conditions can share a status code while remaining separable by a client.
 * Here, {@code DUPLICATE_RESOURCE} lets a sign-up form say "that matricola is
 * already registered" instead of showing a generic conflict message.
 */
@Provider
public class DuplicateResourceExceptionMapper
        implements ExceptionMapper<DuplicateResourceException> {

    private static final Logger LOG =
            LoggerFactory.getLogger(DuplicateResourceExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(DuplicateResourceException exception) {
        LOG.info("Duplicate resource: {}", exception.getMessage());

        return ProblemDetails.build(
                Response.Status.CONFLICT,
                "duplicate-resource",
                "Duplicate Resource",
                exception.getMessage(),
                exception.getErrorCode(),
                uriInfo);
    }
}
