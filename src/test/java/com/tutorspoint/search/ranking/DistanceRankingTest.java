package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;
import static org.assertj.core.api.Assertions.assertThat;

class DistanceRankingTest {

    private final DistanceRanking strategy = new DistanceRanking();

    @Test
    @DisplayName("is selected by 'distance_asc'")
    void key() {
        assertThat(strategy.key()).isEqualTo("distance_asc");
    }

    @Test
    @DisplayName("with an origin: nearest first, verified first at the same distance")
    void withOrigin() {
        RankingContext fromColombo = new RankingContext(null, new GeoPoint(6.9271, 79.8612));

        assertThat(strategy.order(fromColombo))
                .containsExactly(ascending(RankField.DISTANCE), descending(RankField.VERIFIED));
    }

    @Test
    @DisplayName("without an origin there is nothing to measure from, so distance is left out rather than failing")
    void withoutOrigin() {
        assertThat(strategy.order(RankingContext.NONE)).containsExactly(descending(RankField.VERIFIED));
    }

    @Test
    @DisplayName("a tutor with no home base has no distance, which sorts last")
    void distanceIsNullable() {
        assertThat(RankField.DISTANCE.isNullable()).isTrue();
    }
}
