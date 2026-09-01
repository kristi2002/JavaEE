package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.api.dto.response.ProblemDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns Bean Validation failures into <b>400 Bad Request</b> with a per-field
 * breakdown.
 *
 * <h2>Why the default is not good enough</h2>
 * Left alone, the container returns a 400 with an empty or unhelpful body, and
 * the client is told only "something was wrong". The developer integrating with
 * your API then has to guess which of nine fields it was.
 *
 * <p>This mapper produces:
 * <pre>
 * {
 *   "type":   "https://api.unicam.it/problems/validation-failed",
 *   "title":  "Validation Failed",
 *   "status": 400,
 *   "detail": "The request contains 2 invalid field(s)",
 *   "errorCode": "VALIDATION_FAILED",
 *   "correlationId": "a3f9c2e1",
 *   "violations": [
 *     { "field": "studentNumber", "message": "must be a valid student number: exactly 6 digits",
 *       "rejectedValue": "12AB" },
 *     { "field": "email", "message": "email must be a valid address",
 *       "rejectedValue": "not-an-email" }
 *   ]
 * }
 * </pre>
 * Returning ALL the violations rather than only the first matters: a form can
 * highlight every bad field at once instead of making the user fix them one
 * request at a time.
 */
@Provider
public class ConstraintViolationExceptionMapper
        implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConstraintViolationExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        ProblemDetail problem = ProblemDetails.create(
                Response.Status.BAD_REQUEST,
                "validation-failed",
                "Validation Failed",
                "The request contains " + exception.getConstraintViolations().size()
                        + " invalid field(s)",
                "VALIDATION_FAILED",
                uriInfo);

        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            problem.addViolation(
                    extractFieldName(violation.getPropertyPath()),
                    violation.getMessage(),
                    safeRejectedValue(violation.getInvalidValue()));
        }

        LOG.debug("Validation failed with {} violation(s)",
                exception.getConstraintViolations().size());

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(problem)
                .build();
    }

    /**
     * Reduces a Bean Validation property path to the field name a client would
     * recognise.
     *
     * <p>For a validated method parameter the raw path looks like
     * {@code create.request.studentNumber} - the resource method, the parameter,
     * then the field. The client sent JSON and knows nothing about our method
     * names, so we keep only the LAST node.
     *
     * <p>The nested case ({@code email.value}, from the {@code @Valid} embeddable)
     * would also collapse to {@code value}, which is unhelpful - so we keep the
     * last two nodes when the final one is a value-object internal. Small
     * details like this are what make an API pleasant rather than merely correct.
     */
    private String extractFieldName(Path propertyPath) {
        String last = null;
        String secondLast = null;

        for (Path.Node node : propertyPath) {
            if (node.getName() != null) {
                secondLast = last;
                last = node.getName();
            }
        }

        if (last == null) {
            return "request";
        }
        // Email is mapped as an embeddable whose single field is named "value";
        // report it as "email" rather than the meaningless "value".
        if ("value".equals(last) && secondLast != null) {
            return secondLast;
        }
        return last;
    }

    /**
     * Echoes the rejected value back, but never a large or sensitive one.
     *
     * <p>Reflecting input into a response is useful for debugging and is also
     * how you accidentally build an amplification vector or echo a password
     * into a log. Truncate, and return only simple types.
     */
    private Object safeRejectedValue(Object invalidValue) {
        if (invalidValue == null) {
            return null;
        }
        if (invalidValue instanceof Number || invalidValue instanceof Boolean) {
            return invalidValue;
        }
        String asString = String.valueOf(invalidValue);
        return asString.length() > 100 ? asString.substring(0, 100) + "..." : asString;
    }
}
