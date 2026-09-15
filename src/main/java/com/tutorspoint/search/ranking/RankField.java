package com.tutorspoint.search.ranking;

/**
 * Something a result list can be ordered by.
 *
 * <p>A vocabulary, not a column list. A strategy says "rating, highest first" in these words
 * and the repository alone decides what that means in a query - which is what keeps every
 * ranking strategy free of JPA and testable as plain Java.
 */
public enum RankField {

    /** How well the profile matches the keyword; meaningless without one. */
    KEYWORD_RELEVANCE(false),

    /** Verified before unverified: the platform's trust promise (FR-R3). */
    VERIFIED(false),

    /** Accepting, then limited, then full: a tutor with no room is the least useful result. */
    AVAILABILITY(false),

    /** The average star rating. Null for a tutor nobody has reviewed yet. */
    RATING(true),

    REVIEW_COUNT(false),

    /** The bottom of the fee range - the price a parent can expect to start from. */
    FEE(true),

    EXPERIENCE(true),

    /** From the searcher's chosen area to the tutor's home base. Null for online-only tutors. */
    DISTANCE(true);

    private final boolean nullable;

    RankField(boolean nullable) {
        this.nullable = nullable;
    }

    /**
     * Whether a profile can lack this value. The repository sorts those profiles last in
     * either direction: a tutor with no rating is not a tutor with the worst one, and must not
     * top a list sorted from lowest.
     */
    public boolean isNullable() {
        return nullable;
    }
}
