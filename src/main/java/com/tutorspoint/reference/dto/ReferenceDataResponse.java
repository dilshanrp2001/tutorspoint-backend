package com.tutorspoint.reference.dto;

import java.util.List;

/**
 * Every reference list in one response.
 *
 * <p>A search page needs all of these at once to draw its filters. Six requests to fill six
 * dropdowns is six round trips on a phone on mobile data before the page is usable; this is
 * one. The per-list endpoints remain for the screens that need only one of them.
 */
public record ReferenceDataResponse(
        List<ReferenceItemResponse> subjects,
        List<ReferenceItemResponse> examLevels,
        List<ReferenceItemResponse> syllabuses,
        List<ReferenceItemResponse> mediums,
        List<ReferenceItemResponse> classFormats,
        List<DistrictResponse> districts) {
}
