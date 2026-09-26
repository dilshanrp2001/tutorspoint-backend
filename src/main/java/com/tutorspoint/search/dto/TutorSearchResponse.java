package com.tutorspoint.search.dto;

import java.util.List;

/**
 * One page of tutor search results (FR-S1 - FR-S3, FR-S6).
 *
 * @param results      this page, sponsored results first
 * @param page         zero-based page number
 * @param size         the page size asked for; the last page may hold fewer
 * @param totalResults every tutor matching the filters, featured or not. Each appears on
 *                     exactly one page.
 * @param totalPages   pages needed to show all of them
 * @param sort         the ranking applied, so a client that sent no sort knows which it got
 * @param facets       per-value counts for the filter panel
 */
public record TutorSearchResponse(

        List<TutorSearchHit> results,

        int page,

        int size,

        long totalResults,

        int totalPages,

        String sort,

        SearchFacets facets) {
}
