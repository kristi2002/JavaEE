package it.unicam.cs.enrollment.common.logging;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.context.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Member;

/**
 * A CDI PRODUCER METHOD that makes {@code @Inject Logger} work anywhere.
 *
 * <h2>What a producer is for</h2>
 * CDI can inject any class it is allowed to instantiate. But some things cannot
 * be instantiated by CDI at all:
 * <ul>
 *   <li>types from a third-party library with no CDI annotations (like
 *       {@link Logger});</li>
 *   <li>objects created through a factory ({@code LoggerFactory.getLogger});</li>
 *   <li>objects whose construction depends on WHERE they are being injected -
 *       which is exactly our case.</li>
 * </ul>
 * A producer method is the bridge: "when someone asks for a {@code Logger},
 * call this method".
 *
 * <h2>{@link InjectionPoint} - the clever part</h2>
 * CDI passes metadata describing the injection point being satisfied. That lets
 * this one method give every class a logger named after THAT class, so log
 * output still reads:
 * <pre>
 *   INFO  it.unicam.cs.enrollment.service.EnrollmentService - Student enrolled
 * </pre>
 * rather than every line claiming to come from {@code LoggerProducer}.
 *
 * <p>Because the result depends on the injection point, the producer MUST be
 * {@code @Dependent}-scoped (the default). Any wider scope would create the
 * logger once and hand the same instance - named after whichever class happened
 * to ask first - to everybody.
 *
 * <h2>Is this better than the classic static field?</h2>
 * The traditional idiom is:
 * <pre>
 *   private static final Logger LOG = LoggerFactory.getLogger(Foo.class);
 * </pre>
 * That is still the most common form in industry, and it works fine. The
 * producer version removes the copy-pasted line (and the copy-paste bug where
 * {@code Foo.class} is left behind in {@code Bar}), at the cost of one more
 * moving part. Both are shown in this codebase so you recognise each.
 */
@Dependent
public class LoggerProducer {

    /**
     * {@code @Produces} marks this as a factory for {@link Logger} instances.
     * The return type is what CDI matches against injection points.
     */
    @Produces
    public Logger produceLogger(InjectionPoint injectionPoint) {
        return LoggerFactory.getLogger(resolveLoggerName(injectionPoint));
    }

    /**
     * Determines which class the logger should be named after.
     *
     * <p>{@code getMember()} is the field, method or constructor being injected
     * into, and its declaring class is the one we want. The {@code Bean} fallback
     * covers programmatic lookup, where there is no member.
     */
    private String resolveLoggerName(InjectionPoint injectionPoint) {
        Member member = injectionPoint.getMember();
        if (member != null) {
            return member.getDeclaringClass().getName();
        }
        if (injectionPoint.getBean() != null) {
            return injectionPoint.getBean().getBeanClass().getName();
        }
        return LoggerProducer.class.getName();
    }
}
