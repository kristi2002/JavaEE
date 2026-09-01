package it.unicam.cs.enrollment.common;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One page of results plus the metadata a client needs to navigate.
 *
 * <p>Returning a bare {@code List} from a paginated endpoint is a common
 * mistake: the caller receives 20 items and has no idea whether that is
 * everything or the first of 500 pages. The envelope below carries the
 * navigation state with the data.
 *
 * @param <T> element type
 */
public final class Page<T> {

    private final List<T> content;
    private final int pageNumber;
    private final int pageSize;
    private final long totalElements;

    public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
        // Unmodifiable + defensive copy: a caller must not be able to mutate the
        // page after we hand it over.
        this.content = Collections.unmodifiableList(new java.util.ArrayList<>(content));
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
    }

    public static <T> Page<T> of(List<T> content, PageRequest request, long totalElements) {
        return new Page<>(content, request.getPageNumber(), request.getPageSize(), totalElements);
    }

    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(Collections.emptyList(), request.getPageNumber(), request.getPageSize(), 0);
    }

    /**
     * FUNCTOR-STYLE MAPPING: converts {@code Page<Entity>} to {@code Page<Dto>}
     * while preserving all the pagination metadata.
     *
     * <p>Without this, every service method would rebuild the envelope by hand
     * and eventually one of them would get {@code totalElements} wrong. One
     * method, used everywhere, cannot drift.
     */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().map(mapper).collect(Collectors.toList());
        return new Page<>(mapped, pageNumber, pageSize, totalElements);
    }

    public List<T> getContent() {
        return content;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    /** Ceiling division - the idiom for "how many buckets of size n hold x items". */
    public int getTotalPages() {
        if (pageSize == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    public boolean isFirst() {
        return pageNumber == 0;
    }

    public boolean isLast() {
        return pageNumber >= getTotalPages() - 1;
    }

    public boolean hasNext() {
        return !isLast();
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    @Override
    public String toString() {
        return "Page{number=" + pageNumber + ", size=" + pageSize
                + ", total=" + totalElements + ", items=" + content.size() + "}";
    }
}
