package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.List;

import static com.tutorspoint.search.ranking.SortKey.descending;

/** Most years of teaching first; verified first among equals. */
@Component
public class ExperienceRanking implements RankingStrategy {

    public static final String KEY = "experience_desc";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<SortKey> order(RankingContext context) {
        return List.of(descending(RankField.EXPERIENCE), descending(RankField.VERIFIED));
    }
}
