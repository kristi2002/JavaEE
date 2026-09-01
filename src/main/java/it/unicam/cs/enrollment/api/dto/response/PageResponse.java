package it.unicam.cs.enrollment.api.dto.response;

import it.unicam.cs.enrollment.common.Page;

import java.util.List;

/**
 * The JSON envelope for a paginated result.
 *
 * <p>A near-identical twin of {@link Page}, and that is on purpose. {@code Page}
 * is an internal type; this one is the published API contract. Keeping them
 * separate means we can change how pagination works internally - switching to
 * keyset pagination, say - without altering the JSON clients depend on.
 *
 * <p>Duplication between an internal model and an external contract is one of
 * the few kinds of duplication worth keeping. The two change for different
 * reasons and at different times.
 *
 * <pre>
 * {
 *   "content":       [ ... ],
 *   "pageNumber":    0,
 *   "pageSize":      20,
 *   "totalElements": 137,
 *   "totalPages":    7,
 *   "first":         true,
 *   "last":          false,
 *   "hasNext":       true
 * }
 * </pre>
 *
 * @param <T> the type of the items in {@code content}
 */
public class PageResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;

    public PageResponse() {
        // required by JSON-B
    }

    /**
     * Builds the envelope from an internal {@link Page}.
     *
     * <p>A static factory keeps the translation in ONE place. Every resource
     * method that returns a page calls this, so no endpoint can accidentally
     * report {@code totalPages} differently from its neighbours.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setContent(page.getContent());
        response.setPageNumber(page.getPageNumber());
        response.setPageSize(page.getPageSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setFirst(page.isFirst());
        response.setLast(page.isLast());
        response.setHasNext(page.hasNext());
        return response;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
