package it.unicam.cs.enrollment.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Switches JAX-RS on and fixes the base path of every endpoint.
 *
 * <h2>What this three-line class actually does</h2>
 * Extending {@link Application} and annotating it with {@code @ApplicationPath}
 * is the modern, XML-free way to activate JAX-RS. Without it the container never
 * scans for {@code @Path} classes and every endpoint returns 404.
 *
 * <p>Because we do NOT override {@code getClasses()} or {@code getSingletons()},
 * the container auto-discovers every {@code @Path} resource and {@code @Provider}
 * in the archive. Overriding either method switches to explicit registration -
 * you then have to list every class by hand, and forgetting one is a silent
 * failure. Auto-discovery is almost always what you want.
 *
 * <h2>How a URL is assembled</h2>
 * <pre>
 *   http://localhost:8080 / enrollment / api      / students / 42
 *   \_________________/     \________/   \_/        \______/   \/
 *      host:port            context      this        @Path on   @Path on
 *                            root      annotation    resource    method
 * </pre>
 * The context root comes from the WAR filename, which the {@code <finalName>}
 * element in {@code pom.xml} sets to {@code enrollment}.
 */
@ApplicationPath("/api")
public class JaxRsActivator extends Application {
    // Intentionally empty: auto-discovery handles registration.
}
