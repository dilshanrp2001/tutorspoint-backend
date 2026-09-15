package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;
import static org.assertj.core.api.Assertions.assertThat;

class PriceRankingTest {

    private final PriceRanking strategy = new PriceRanking();

    @Test
    @DisplayName("is selected by 'price_asc'")
    void key() {
        assertThat(strategy.key()).isEqualTo("price_asc");
    }

    @Test
    @DisplayName("orders by starting fee, lowest first, verified first at the same fee")
    void order() {
        assertThat(strategy.order(RankingContext.NONE))
                .containsExactly(ascending(RankField.FEE), descending(RankField.VERIFIED));
    }

    @Test
    @DisplayName("a keyword or an origin does not change it")
    void contextIndependent() {
        assertThat(strategy.order(new RankingContext("maths", new GeoPoint(6.9, 79.8))))
                .isEqualTo(strategy.order(RankingContext.NONE));
    }

    @Test
    @DisplayName("fee is sorted with missing values last")
    void feeIsNullable() {
        assertThat(RankField.FEE.isNullable()).isTrue();
    }
}
