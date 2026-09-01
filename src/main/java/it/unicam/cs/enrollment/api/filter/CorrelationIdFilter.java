package it.unicam.cs.enrollment.api.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Gives every request a CORRELATION ID and logs its outcome.
 *
 * <h2>The problem this solves</h2>
 * A production log is a single stream with hundreds of interleaved requests. A
 * user reports an error; you find the exception; and you cannot tell which of
 * the forty log lines before it belong to the same request. Reconstructing what
 * happened becomes guesswork.
 *
 * <p>A correlation id fixes this completely. One identifier is attached to
 * every log line of a request, returned in the response header, and included in
 * error bodies. "Search for {@code a3f9c2e1}" replaces an hour of archaeology.
 *
 * <p>In a microservice system the id is PROPAGATED: service A passes its id in
 * the header to service B, so one identifier traces a request across the whole
 * estate. That is why this filter honours an INBOUND header when present and
 * only generates a new id if there is none. The idea generalises into
 * distributed tracing (OpenTelemetry, Jaeger, Zipkin), where the same principle
 * carries spans and timings as well.
 *
 * <h2>MDC - Mapped Diagnostic Context</h2>
 * {@link MDC} is a THREAD-LOCAL map that SLF4J makes available to the log
 * layout. Put the id in it once and every subsequent log statement on that
 * thread can include it, without a single method having to take it as a
 * parameter. The log pattern references it as {@code %X{correlationId}} - see
 * {@code docker/wildfly/configure.cli}.
 *
 * <p><b>Thread-locals must be cleaned up.</b> Application servers reuse threads
 * from a pool, so a value left behind is inherited by the next, unrelated
 * request - which is both confusing and a genuine data-leak risk. The
 * {@code finally}-equivalent here is the response filter, which always runs.
 *
 * <h2>Filters versus interceptors</h2>
 * Two different JAX-RS extension points, easily confused:
 * <ul>
 *   <li>FILTERS ({@code ContainerRequestFilter}/{@code ContainerResponseFilter})
 *       see headers, URIs and status codes. Use for cross-cutting HTTP concerns:
 *       auth, CORS, logging, correlation ids.</li>
 *   <li>INTERCEPTORS ({@code ReaderInterceptor}/{@code WriterInterceptor}) wrap
 *       the entity stream. Use for payload concerns: compression, encryption.</li>
 * </ul>
 */
@Provider
@PreMatching
@Priority(1)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /** Conventional header name. {@code X-Request-Id} is an equally common spelling. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** MDC key. Must match {@code %X{correlationId}} in the server's log pattern. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Request-scoped property used to carry the start time to the response filter. */
    private static final String START_TIME_PROPERTY = "request.startTimeNanos";

    /**
     * Runs BEFORE the request is matched to a resource method.
     *
     * <p>{@code @PreMatching} matters: without it the filter runs after routing,
     * so a request that 404s because no resource matched would never get an id -
     * and those are exactly the requests you want to be able to trace.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String correlationId = requestContext.getHeaderString(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.trim().isEmpty()) {
            // Short random id: readable in a log line, and collision-safe enough
            // for correlating requests. A full UUID works too and is more common
            // when ids cross service boundaries.
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        } else {
            // NEVER trust an inbound header verbatim. It is attacker-controlled,
            // and it is about to be written into log files that other tools
            // parse. A newline here would let a caller forge whole log entries -
            // an attack called LOG INJECTION or log forging.
            correlationId = sanitise(correlationId);
        }

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());

        LOG.info("--> {} {}",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri().getPath());
    }

    /**
     * Runs after the resource method, whatever the outcome - including when an
     * exception mapper produced the response.
     */
    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        try {
            String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

            if (correlationId != null) {
                // Echo it back so the CLIENT can quote it in a bug report.
                MultivaluedMap<String, Object> headers = responseContext.getHeaders();
                headers.putSingle(CORRELATION_ID_HEADER, correlationId);
            }

            Object startTime = requestContext.getProperty(START_TIME_PROPERTY);
            long elapsedMillis = startTime instanceof Long
                    ? (System.nanoTime() - (Long) startTime) / 1_000_000
                    : -1;

            int status = responseContext.getStatus();
            String line = "<-- {} {} {} ({}ms)";
            Object[] args = {
                    requestContext.getMethod(),
                    requestContext.getUriInfo().getRequestUri().getPath(),
                    status,
                    elapsedMillis
            };

            // Server errors are WARN, everything else INFO. Log levels are a
            // signal to whoever is on call: if 4xx responses (a client sending
            // bad input) were logged at ERROR, real problems would drown in noise.
            if (status >= 500) {
                LOG.warn(line, args);
            } else {
                LOG.info(line, args);
            }

        } finally {
            // ALWAYS clear the MDC. In a finally block, so an exception in the
            // logging above cannot leave the thread contaminated.
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Strips anything that could break a log line or bloat it, and caps the
     * length. Allow-listing safe characters is more robust than blocking known
     * bad ones - you cannot forget an entry in an allow-list.
     */
    private String sanitise(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
