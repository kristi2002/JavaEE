package it.unicam.cs.enrollment.common.logging;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Implements {@link Loggable}: times every intercepted call, logs entry and
 * exit, and flags slow ones.
 *
 * <h2>{@code @Priority} - enabling and ordering</h2>
 * Before CDI 1.1 an interceptor had to be listed in {@code beans.xml} to be
 * active. {@code @Priority} both ENABLES it globally and defines where it sits
 * in the chain. Lower numbers run further out (closer to the caller).
 *
 * <p>{@link Interceptor.Priority} defines the standard bands:
 * <pre>
 *   PLATFORM_BEFORE  =    0   security, then...
 *   LIBRARY_BEFORE   = 1000
 *   APPLICATION      = 2000   &lt;- your own interceptors
 *   LIBRARY_AFTER    = 3000
 *   PLATFORM_AFTER   = 4000   transactions
 * </pre>
 * We use {@code APPLICATION + 10}. Since {@code @Transactional} lives in the
 * PLATFORM_AFTER band, our interceptor runs OUTSIDE the transaction - so the
 * duration we measure includes commit time, which is usually what you want,
 * since commit is often where the time actually goes.
 */
@Loggable
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class LoggingInterceptor {

    /**
     * The classic static logger, shown here alongside the injected version used
     * elsewhere. An interceptor is a good place for it: the logger name should
     * be the interceptor's, not the intercepted class's, so that this
     * diagnostic channel can be turned up or down on its own.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    /**
     * The interception point. Exactly one {@code @AroundInvoke} method per
     * interceptor, and its signature is fixed by the specification:
     * {@code Object <name>(InvocationContext ctx) throws Exception}.
     *
     * <p>{@link InvocationContext#proceed()} continues the chain: the next
     * interceptor, or the real method if this is the last one. FORGETTING TO
     * CALL {@code proceed()} silently stops the target method from ever
     * executing - a memorable bug to debug the first time.
     */
    @AroundInvoke
    public Object logInvocation(InvocationContext context) throws Exception {
        Method method = context.getMethod();
        Loggable config = resolveConfiguration(method);

        String target = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        long startNanos = System.nanoTime();

        if (LOG.isDebugEnabled()) {
            // GUARDED LOGGING. The check avoids building the message when DEBUG
            // is off. With SLF4J's {} placeholders the formatting is already
            // deferred, so the guard mainly pays off when the arguments
            // themselves are expensive to produce - as Arrays.toString is.
            if (config != null && config.logArguments()) {
                LOG.debug("-> {}({})", target, Arrays.toString(context.getParameters()));
            } else {
                LOG.debug("-> {}", target);
            }
        }

        try {
            Object result = context.proceed();

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long threshold = config != null ? config.slowCallThresholdMillis() : 500L;

            if (elapsedMillis >= threshold) {
                LOG.warn("<- {} completed in {}ms (slow, threshold {}ms)",
                        target, elapsedMillis, threshold);
            } else {
                LOG.debug("<- {} completed in {}ms", target, elapsedMillis);
            }
            return result;

        } catch (Exception e) {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // Log the type and message, NOT the full stack trace. The exception
            // is being rethrown, so whoever finally handles it will log it
            // properly. Logging a stack trace at every level on the way out is
            // how one failure becomes forty pages of duplicated log.
            LOG.debug("<- {} threw {} after {}ms: {}",
                    target, e.getClass().getSimpleName(), elapsedMillis, e.getMessage());

            // RETHROW. An interceptor that swallows exceptions changes the
            // semantics of the method it wraps, and in a transactional context
            // it would also suppress the rollback.
            throw e;
        }
    }

    /**
     * Finds the {@code @Loggable} that applies: the method-level annotation wins
     * over the class-level one, mirroring how the container resolves the binding.
     */
    private Loggable resolveConfiguration(Method method) {
        Loggable methodLevel = method.getAnnotation(Loggable.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return method.getDeclaringClass().getAnnotation(Loggable.class);
    }
}
