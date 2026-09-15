package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;
import static org.assertj.core.api.Assertions.assertThat;

class RelevanceRankingTest {

    private final RelevanceRanking strategy = new RelevanceRanking();

    @Test
    @DisplayName("is the default, selected by 'relevance'")
    void key() {
        assertThat(strategy.key()).isEqualTo("relevance");
    }

    @Test
    @DisplayName("without a keyword: verified, then availability, then rating and review count")
    void withoutKeyword() {
        assertThat(strategy.order(RankingContext.NONE)).containsExactly(
                descending(RankField.VERIFIED),
                ascending(RankField.AVAILABILITY),
                descending(RankField.RATING),
                descending(RankField.REVIEW_COUNT));
    }

    @Test
    @DisplayName("with a keyword: the textual match leads, ahead of everything else")
    void withKeyword() {
        assertThat(strategy.order(new RankingContext("chemistry", null))).containsExactly(
                descending(RankField.KEYWORD_RELEVANCE),
                descending(RankField.VERIFIED),
                ascending(RankField.AVAILABILITY),
                descending(RankField.RATING),
                descending(RankField.REVIEW_COUNT));
    }

    @Test
    @DisplayName("a blank keyword is no keyword")
    void blankKeyword() {
        assertThat(strategy.order(new RankingContext("   ", null))).doesNotContain(descending(RankField.KEYWORD_RELEVANCE));
    }

    @Test
    @DisplayName("an origin does not change it: relevance is not about distance")
    void ignoresOrigin() {
        assertThat(strategy.order(new RankingContext(null, new GeoPoint(6.9, 79.8))))
                .isEqualTo(strategy.order(RankingContext.NONE));
    }
}
