package it.unicam.cs.enrollment.common;

/**
 * An immutable description of "which slice of the results do I want".
 *
 * <h2>Why pagination is not optional</h2>
 * {@code SELECT * FROM students} works beautifully against the 20 rows in your
 * development database and takes the production server down at 40,000. Any
 * endpoint that returns a collection must be paginated from the very first
 * version - retrofitting it later is a breaking API change.
 *
 * <h2>Offset pagination vs keyset pagination</h2>
 * This class models OFFSET pagination ({@code LIMIT}/{@code OFFSET}), which is
 * what almost every application starts with. Know its two weaknesses:
 * <ul>
 *   <li>The database must count past every skipped row, so page 10,000 is slow.</li>
 *   <li>If a row is inserted while the user is paging, items shift and can be
 *       seen twice or missed.</li>
 * </ul>
 * The alternative is KEYSET pagination ("seek method"):
 * {@code WHERE id > :lastSeenId ORDER BY id LIMIT 20}. It is constant-time at
 * any depth and stable under concurrent inserts, at the price of not being able
 * to jump to an arbitrary page number. Use it for infinite scroll and for large
 * data sets.
 */
public final class PageRequest {

    /**
     * A HARD CEILING on page size. Without it, a client can send
     * {@code ?size=1000000} and turn your paginated endpoint back into the
     * unbounded query you were trying to avoid. Treat this as a security
     * control, not a nicety: it is a denial-of-service guard.
     */
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DEFAULT_PAGE_SIZE = 20;

    private final int pageNumber;
    private final int pageSize;

    private PageRequest(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    /**
     * Builds a request, CLAMPING rather than rejecting out-of-range input.
     *
     * <p>This is a deliberate API design decision. For pagination parameters,
     * silently correcting nonsense ({@code page=-3} becomes page 0) is friendlier
     * than a 400 and cannot be exploited. For anything that carries business
     * meaning, do the opposite: reject loudly rather than guess.
     *
     * @param pageNumber zero-based page index
     * @param pageSize   number of items per page
     */
    public static PageRequest of(int pageNumber, int pageSize) {
        int safePage = Math.max(0, pageNumber);
        int safeSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new PageRequest(safePage, safeSize);
    }

    public static PageRequest first() {
        return of(0, DEFAULT_PAGE_SIZE);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * Translates the page number into the row offset JPA wants.
     * Maps to {@code Query.setFirstResult(int)}.
     */
    public int getOffset() {
        return pageNumber * pageSize;
    }

    @Override
    public String toString() {
        return "PageRequest{page=" + pageNumber + ", size=" + pageSize + "}";
    }
}
