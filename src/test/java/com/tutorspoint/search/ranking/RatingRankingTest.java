package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tutorspoint.search.ranking.SortKey.descending;
import static org.assertj.core.api.Assertions.assertThat;

class RatingRankingTest {

    private final RatingRanking strategy = new RatingRanking();

    @Test
    @DisplayName("is selected by 'rating_desc'")
    void key() {
        assertThat(strategy.key()).isEqualTo("rating_desc");
    }

    @Test
    @DisplayName("orders by average rating, then by how many reviews back it, then verified")
    void order() {
        assertThat(strategy.order(RankingContext.NONE)).containsExactly(
                descending(RankField.RATING),
                descending(RankField.REVIEW_COUNT),
                descending(RankField.VERIFIED));
    }

    @Test
    @DisplayName("an unreviewed tutor has no rating, which sorts last rather than as the lowest")
    void ratingIsNullable() {
        assertThat(RankField.RATING.isNullable()).isTrue();
        assertThat(RankField.REVIEW_COUNT.isNullable()).isFalse();
    }
}
