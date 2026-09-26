package com.tutorspoint.search.ranking;

/** One step of an ordering: a field and which way it runs. */
public record SortKey(RankField field, boolean descending) {

    public SortKey {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
    }

    public static SortKey ascending(RankField field) {
        return new SortKey(field, false);
    }

    public static SortKey descending(RankField field) {
        return new SortKey(field, true);
    }
}
