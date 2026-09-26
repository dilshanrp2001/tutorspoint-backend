package com.tutorspoint.search.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalisation the compact constructor applies before validation, and the two cross-field
 * rules. The per-field annotations are exercised through the real binder in {@code TutorSearchIT}.
 */
class TutorSearchCriteriaTest {

    @Test
    @DisplayName("absent paging becomes the first page of the default size")
    void pagingDefaults() {
        TutorSearchCriteria criteria = TutorSearchCriteria.unfiltered();

        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(SearchValidation.DEFAULT_PAGE_SIZE);
        assertThat(criteria.sort()).isNull();
    }

    @Test
    @DisplayName("codes are trimmed and upper-cased, the sort lower-cased, and blanks dropped")
    void normalises() {
        TutorSearchCriteria criteria = new TutorSearchCriteria(" chemistry ", "gce_al", "", null, null, "  ",
                null, null, null, null, null, "  a/l chem  ", " PRICE_ASC ", 2, 10);

        assertThat(criteria.subject()).isEqualTo("CHEMISTRY");
        assertThat(criteria.examLevel()).isEqualTo("GCE_AL");
        assertThat(criteria.syllabus()).isNull();
        assertThat(criteria.area()).isNull();
        assertThat(criteria.keyword()).isEqualTo("a/l chem");
        assertThat(criteria.sort()).isEqualTo("price_asc");
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("a price band may be open at either end, but not inverted")
    void feeBand() {
        assertThat(withFees(null, null).isFeeBandOrdered()).isTrue();
        assertThat(withFees("1000", null).isFeeBandOrdered()).isTrue();
        assertThat(withFees(null, "1000").isFeeBandOrdered()).isTrue();
        assertThat(withFees("1000", "1000").isFeeBandOrdered()).isTrue();
        assertThat(withFees("2000", "1000").isFeeBandOrdered()).isFalse();
    }

    @Test
    @DisplayName("distance sorting needs an area; other sorts do not")
    void distanceNeedsAnArea() {
        assertThat(withSortAndArea("distance_asc", null).isDistanceSortAnchored()).isFalse();
        assertThat(withSortAndArea("DISTANCE_ASC", null).isDistanceSortAnchored()).isFalse();
        assertThat(withSortAndArea("distance_asc", "COLOMBO").isDistanceSortAnchored()).isTrue();
        assertThat(withSortAndArea("price_asc", null).isDistanceSortAnchored()).isTrue();
        assertThat(withSortAndArea(null, null).isDistanceSortAnchored()).isTrue();
    }

    @Test
    @DisplayName("only true switches the yes/no filters on")
    void booleanFilters() {
        assertThat(withFlags(true, true).onlineOnly()).isTrue();
        assertThat(withFlags(true, true).verifiedOnlyRequested()).isTrue();
        assertThat(withFlags(false, false).onlineOnly()).isFalse();
        assertThat(withFlags(null, null).verifiedOnlyRequested()).isFalse();
    }

    private static TutorSearchCriteria withFees(String min, String max) {
        return new TutorSearchCriteria(null, null, null, null, null, null,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                null, null, null, null, null, null, null);
    }

    private static TutorSearchCriteria withSortAndArea(String sort, String area) {
        return new TutorSearchCriteria(null, null, null, null, null, area, null, null,
                null, null, null, null, sort, null, null);
    }

    private static TutorSearchCriteria withFlags(Boolean online, Boolean verified) {
        return new TutorSearchCriteria(null, null, null, null, null, null, null, null,
                online, verified, null, null, null, null, null);
    }
}
