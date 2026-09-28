package com.tutorspoint.search.dto;

/**
 * How many tutors one filter value would yield.
 *
 * @param code  the reference or enum code, as the matching query parameter takes it
 * @param count matching tutors if this value were chosen, with every other filter unchanged
 */
public record FacetCount(

        String code,

        long count) {
}
