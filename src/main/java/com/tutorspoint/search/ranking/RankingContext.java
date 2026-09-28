package com.tutorspoint.search.ranking;

import java.util.Optional;

/**
 * What a strategy may take into account beyond its own rule: the keyword, if the searcher
 * typed one, and where they are searching from, if they chose an area.
 *
 * @param keyword the trimmed keyword, or null
 * @param origin  the centre of the searcher's chosen area, or null
 */
public record RankingContext(String keyword, GeoPoint origin) {

    public static final RankingContext NONE = new RankingContext(null, null);

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public Optional<GeoPoint> originPoint() {
        return Optional.ofNullable(origin);
    }
}
