package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.List;

import static com.tutorspoint.search.ranking.SortKey.descending;

/**
 * Best reviewed first. Between two equal averages the one backed by more reviews leads - a
 * 4.8 from forty parents says more than a 4.8 from two. Unreviewed tutors come last.
 */
@Component
public class RatingRanking implements RankingStrategy {

    public static final String KEY = "rating_desc";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<SortKey> order(RankingContext context) {
        return List.of(
                descending(RankField.RATING),
                descending(RankField.REVIEW_COUNT),
                descending(RankField.VERIFIED));
    }
}
