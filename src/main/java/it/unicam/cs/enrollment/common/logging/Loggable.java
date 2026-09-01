package it.unicam.cs.enrollment.common.logging;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An INTERCEPTOR BINDING: annotate a class or method with {@code @Loggable} and
 * every invocation is timed and logged.
 *
 * <h2>The problem interceptors solve</h2>
 * Timing and logging are CROSS-CUTTING CONCERNS: needed in many places, related
 * to none of them. Written by hand, every service method turns into
 * <pre>
 *   long start = System.nanoTime();
 *   log.debug("entering enroll({}, {})", studentId, courseId);
 *   try {
 *       ... three lines of actual business logic ...
 *   } finally {
 *       log.debug("exiting enroll in {}ms", elapsed);
 *   }
 * </pre>
 * The business logic drowns. This is what Aspect-Oriented Programming (AOP) was
 * invented for, and Jakarta EE ships it as interceptors.
 *
 * <h2>How the three pieces fit together</h2>
 * <ol>
 *   <li>This annotation - the BINDING. It is a marker; it contains no logic.</li>
 *   <li>{@link LoggingInterceptor} - the BEHAVIOUR, annotated with
 *       {@code @Interceptor} and with this binding.</li>
 *   <li>The target class or method carrying {@code @Loggable}.</li>
 * </ol>
 * The container weaves them together by generating a proxy. You will meet the
 * same pattern in {@code @Transactional}, {@code @Asynchronous} and
 * {@code @RolesAllowed} - all of them are interceptor bindings provided by the
 * platform.
 *
 * <h2>{@code @Inherited} and {@code @Nonbinding}</h2>
 * <ul>
 *   <li>{@code @Inherited} - a subclass of an annotated class is intercepted too.</li>
 *   <li>{@code @Nonbinding} - by default, ATTRIBUTE VALUES are part of the
 *       binding: {@code @Loggable(level="INFO")} and {@code @Loggable(level="DEBUG")}
 *       would be considered different bindings and would need different
 *       interceptors. {@code @Nonbinding} says "this attribute is data for the
 *       interceptor, not part of matching it".</li>
 * </ul>
 */
@Inherited
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {

    /**
     * Log method arguments as well as timings.
     *
     * <p>Defaults to {@code false} ON PURPOSE. Arguments routinely contain
     * personal data, passwords or tokens, and logs are copied to systems with
     * far weaker access controls than your database. "Log everything and filter
     * later" is how organisations end up with credentials in their log
     * aggregator. Opt in per method, deliberately.
     */
    @Nonbinding
    boolean logArguments() default false;

    /**
     * Calls slower than this (in milliseconds) are logged at WARN instead of
     * DEBUG. A cheap, always-on performance tripwire.
     */
    @Nonbinding
    long slowCallThresholdMillis() default 500L;
}
