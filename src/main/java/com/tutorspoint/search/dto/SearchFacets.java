package com.tutorspoint.search.dto;

import java.util.List;

/**
 * Per-value result counts for each filter, so the filter panel can say "Chemistry (14)"
 * before the parent taps it.
 *
 * <p>Each list is counted with every <em>other</em> filter applied and its own left out. That
 * is what makes the number mean "what you would get if you chose this": with Physics selected,
 * the Chemistry count is the tutors who would match if Chemistry replaced it, not zero.
 *
 * <p>Codes only, no names: the client already holds the translated reference lists it built the
 * filter panel from. Values with no matching tutor are omitted; absent means zero.
 */
public record SearchFacets(

        List<FacetCount> subjects,

        List<FacetCount> examLevels,

        List<FacetCount> syllabuses,

        List<FacetCount> mediums,

        List<FacetCount> classFormats,

        /* Districts and towns alike, counted the way the area filter matches. */
        List<FacetCount> areas) {
}
