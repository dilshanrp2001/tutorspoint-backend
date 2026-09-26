package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tutorspoint.search.ranking.SortKey.descending;
import static org.assertj.core.api.Assertions.assertThat;

class ExperienceRankingTest {

    private final ExperienceRanking strategy = new ExperienceRanking();

    @Test
    @DisplayName("is selected by 'experience_desc'")
    void key() {
        assertThat(strategy.key()).isEqualTo("experience_desc");
    }

    @Test
    @DisplayName("orders by years of experience, most first, verified first among equals")
    void order() {
        assertThat(strategy.order(RankingContext.NONE))
                .containsExactly(descending(RankField.EXPERIENCE), descending(RankField.VERIFIED));
    }

    @Test
    @DisplayName("a keyword or an origin does not change it")
    void contextIndependent() {
        assertThat(strategy.order(new RankingContext("physics", new GeoPoint(7.0, 80.0))))
                .isEqualTo(strategy.order(RankingContext.NONE));
    }
}
