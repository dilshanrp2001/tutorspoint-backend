package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tutorspoint.search.ranking.SortKey.ascending;
import static com.tutorspoint.search.ranking.SortKey.descending;

/**
 * Nearest first: the Haversine distance from the centre of the searcher's chosen area to the
 * centre of each tutor's home base. Tutors with no home base - online-only - come last.
 *
 * <p>The request is refused without an area, so in practice there is always an origin. The
 * fallback when there is not (an area code that names nothing, which also matches no tutors)
 * is to leave distance out rather than to throw: an empty page is the right answer to that
 * request, and an error is not.
 */
@Component
public class DistanceRanking implements RankingStrategy {

    public static final String KEY = "distance_asc";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<SortKey> order(RankingContext context) {
        List<SortKey> order = new ArrayList<>();
        context.originPoint().ifPresent(origin -> order.add(ascending(RankField.DISTANCE)));
        order.add(descending(RankField.VERIFIED));
        return List.copyOf(order);
    }
}
