package com.example.store.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reorders a separately fetch-joined list back into the order of a plain, paginated id page.
 * Needed because collection fetch joins can't be combined with LIMIT/OFFSET in the same query -
 * see the repository methods this is used alongside.
 */
final class PageSupport {
    private PageSupport() {}

    static <E> Page<E> reorder(Page<E> idPage, Function<E, Long> idExtractor, List<E> fetched) {
        Map<Long, E> byId = fetched.stream().collect(Collectors.toMap(idExtractor, e -> e));
        List<E> ordered =
                idPage.getContent().stream().map(idExtractor).map(byId::get).toList();
        return new PageImpl<>(ordered, idPage.getPageable(), idPage.getTotalElements());
    }
}
