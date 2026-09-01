package it.unicam.cs.enrollment.common;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Supplies the application's notion of "now" as an INJECTABLE dependency.
 *
 * <h2>Why not just call {@code Instant.now()}?</h2>
 * Because a static call to the system clock is an UNTESTABLE DEPENDENCY hidden
 * inside your business logic. Consider the rule "you may only enrol while the
 * window is open". With {@code Instant.now()} buried in the service, testing it
 * means one of:
 * <ul>
 *   <li>changing the machine's clock - absurd;</li>
 *   <li>constructing courses whose window happens to surround the real current
 *       time - fragile, and impossible to test the boundary instants;</li>
 *   <li>a mocking library that rewrites static methods - heavy machinery to
 *       compensate for a design problem.</li>
 * </ul>
 *
 * <p>Injecting a {@link Clock} makes time an ordinary collaborator. Production
 * gets {@code Clock.systemUTC()}; a test passes
 * {@code Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC)} and
 * can assert behaviour one nanosecond before and after a deadline. This is the
 * standard treatment for any non-deterministic input: time, randomness, UUID
 * generation, the file system.
 *
 * <h2>Why UTC?</h2>
 * Servers store and compute in UTC, always. Local time zones are a PRESENTATION
 * concern, applied at the edge when formatting for a human. Mixing the two in
 * the domain is how you get the classic bugs where something misbehaves for one
 * hour twice a year.
 */
@Dependent
public class ClockProducer {

    /**
     * Deliberately {@code @Dependent} (the default scope), not
     * {@code @ApplicationScoped}.
     *
     * <p>A normal-scoped bean is injected as a PROXY, and proxying requires the
     * class to be non-final with an accessible no-arg constructor. Rather than
     * depend on those details of an unrelated JDK class, we let each injection
     * point hold its own reference. {@code Clock.systemUTC()} is immutable,
     * thread-safe and free to create, so there is nothing to gain from sharing
     * one instance.
     */
    @Produces
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
