package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;

/**
 * The default: the tutors most worth contacting first.
 *
 * <p>With a keyword, the closest textual match leads. Without one, or among equally good
 * matches, it falls back on what the platform exists to promise - verified before unverified,
 * then tutors with room before tutors without, then the best reviewed.
 */
@Component
public class RelevanceRanking implements RankingStrategy {

    public static final String KEY = "relevance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<SortKey> order(RankingContext context) {
        List<SortKey> order = new ArrayList<>();
        if (context.hasKeyword()) {
            order.add(descending(RankField.KEYWORD_RELEVANCE));
        }
        order.add(descending(RankField.VERIFIED));
        order.add(ascending(RankField.AVAILABILITY));
        order.add(descending(RankField.RATING));
        order.add(descending(RankField.REVIEW_COUNT));
        return List.copyOf(order);
    }
}
