package com.example.store.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Combines a plain, paginated id page with a separately fetch-joined list for those same ids
 * into an ordered {@link PagedModel}. Needed because collection fetch joins can't be combined
 * with LIMIT/OFFSET in the same query - see the repository methods this is used alongside.
 */
final class PageSupport {
    private PageSupport() {}

    static <E, D> PagedModel<D> toPagedModel(
            Page<E> idPage, Function<E, Long> idExtractor, List<E> fetched, Function<E, D> mapper) {
        Map<Long, E> byId = fetched.stream().collect(Collectors.toMap(idExtractor, e -> e));
        List<D> dtos = idPage.getContent().stream()
                .map(idExtractor)
                .map(byId::get)
                .map(mapper)
                .toList();
        return new PagedModel<>(new PageImpl<>(dtos, idPage.getPageable(), idPage.getTotalElements()));
    }
}
