package it.unicam.cs.enrollment.api.exception;

import it.unicam.cs.enrollment.exception.BusinessRuleViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link BusinessRuleViolationException} into <b>409 Conflict</b>.
 *
 * <h2>409 versus 422</h2>
 * Both are defensible for "your request was well-formed but I refuse it":
 * <ul>
 *   <li><b>409 Conflict</b> - the request conflicts with the CURRENT STATE of
 *       the resource. "The course is full" is exactly that: the same request
 *       would have succeeded an hour ago.</li>
 *   <li><b>422 Unprocessable Content</b> - the request is semantically wrong
 *       regardless of state.</li>
 * </ul>
 * Most of our rules are state-dependent, so 409 fits. What matters far more than
 * which you pick is that you pick ONE and apply it consistently, and that the
 * body carries a machine-readable code so clients never have to parse prose.
 *
 * <p>The {@code errorCode} in the response ({@code COURSE_FULL},
 * {@code PREREQUISITES_NOT_MET}, ...) is what a front-end switches on to show
 * the right message in the right language.
 */
@Provider
public class BusinessRuleViolationExceptionMapper
        implements ExceptionMapper<BusinessRuleViolationException> {

    private static final Logger LOG =
            LoggerFactory.getLogger(BusinessRuleViolationExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(BusinessRuleViolationException exception) {
        // INFO: a refused business rule is the system working correctly, but it
        // IS worth recording - a sudden spike in COURSE_FULL is useful signal.
        LOG.info("Business rule violation [{}]: {}",
                exception.getErrorCode(), exception.getMessage());

        return ProblemDetails.build(
                Response.Status.CONFLICT,
                "business-rule-violation",
                "Business Rule Violation",
                exception.getMessage(),
                exception.getErrorCode(),
                uriInfo);
    }
}
