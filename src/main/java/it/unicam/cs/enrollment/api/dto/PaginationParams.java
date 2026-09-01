package it.unicam.cs.enrollment.api.dto;

import it.unicam.cs.enrollment.common.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

/**
 * Reusable {@code ?page=&amp;size=} query parameters, injected with
 * {@code @BeanParam}.
 *
 * <h2>What {@code @BeanParam} is for</h2>
 * Without it, every paginated endpoint repeats:
 * <pre>
 *   public Response list(&#64;QueryParam("page") &#64;DefaultValue("0") int page,
 *                        &#64;QueryParam("size") &#64;DefaultValue("20") int size) {
 * </pre>
 * {@code @BeanParam} lets you gather related parameters into one class and
 * inject it as a single argument. The defaults and the bounds are then declared
 * once, so no endpoint can drift to a different default page size.
 *
 * <p>The same annotation works for {@code @PathParam}, {@code @HeaderParam},
 * {@code @CookieParam}, {@code @FormParam} and {@code @MatrixParam} - a
 * {@code @BeanParam} class can mix all of them.
 *
 * <h2>Belt and braces on the bounds</h2>
 * {@code @Min}/{@code @Max} reject out-of-range values with a 400, and
 * {@code PageRequest.of} clamps whatever gets through. Two layers, because the
 * annotations only fire when the container validates, and
 * {@code PageRequest.of} is also called from code paths that never touch HTTP.
 */
public class PaginationParams {

    @QueryParam("page")
    @DefaultValue("0")
    @Min(value = 0, message = "page must be 0 or greater")
    private int page;

    @QueryParam("size")
    @DefaultValue("20")
    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 100, message = "size must be at most 100")
    private int size;

    public PaginationParams() {
        // JAX-RS instantiates this
    }

    /** Converts the HTTP-level parameters into the internal type. */
    public PageRequest toPageRequest() {
        return PageRequest.of(page, size);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
