package it.unicam.cs.enrollment.api.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an optimistic-locking failure into <b>409 Conflict</b>.
 *
 * <h2>When this fires</h2>
 * Two users open the same course, both edit it, both save. The first commit
 * succeeds and bumps {@code version} from 3 to 4. The second still believes it
 * is on version 3, its {@code UPDATE ... WHERE version = 3} matches no rows, and
 * the provider raises {@link OptimisticLockException}.
 *
 * <p>Without this mapper the user sees a 500 and assumes the system is broken.
 * It is not broken - it just prevented the LOST UPDATE that would have silently
 * discarded the first user's work. The response should say so.
 *
 * <p>{@code Retry-After: 0} hints that retrying immediately is reasonable, since
 * the correct client behaviour is to re-read the resource, re-apply the change
 * on top of the newer version, and submit again. A well-built client can do that
 * automatically for non-conflicting edits.
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    private static final Logger LOG = LoggerFactory.getLogger(OptimisticLockExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(OptimisticLockException exception) {
        // WARN: not an application bug, but a burst of these means two workflows
        // are fighting over the same rows and the design may need attention.
        LOG.warn("Optimistic lock conflict: {}", exception.getMessage());

        return Response.fromResponse(ProblemDetails.build(
                        Response.Status.CONFLICT,
                        "concurrent-modification",
                        "Concurrent Modification",
                        "This record was modified by someone else while you were editing it. "
                                + "Reload it and apply your changes again.",
                        "OPTIMISTIC_LOCK_CONFLICT",
                        uriInfo))
                .header("Retry-After", "0")
                .build();
    }
}
