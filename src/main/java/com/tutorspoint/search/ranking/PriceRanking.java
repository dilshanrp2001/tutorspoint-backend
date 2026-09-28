package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.List;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;

/** Cheapest first, by the bottom of each tutor's fee range; verified first at the same price. */
@Component
public class PriceRanking implements RankingStrategy {

    public static final String KEY = "price_asc";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<SortKey> order(RankingContext context) {
        return List.of(ascending(RankField.FEE), descending(RankField.VERIFIED));
    }
}
