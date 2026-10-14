package com.payflow.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

// Concept: generics with two type parameters on a static factory, E (entity) mapped to T (DTO).
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public static <T> PageResponse<T> of(List<T> items, Page<?> page) {
        return new PageResponse<>(List.copyOf(items), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
