package com.tutorspoint.search.ranking;

import java.util.ArrayList;
import java.util.List;

/**
 * The sponsored-slot rule (FR-S6): featured tutors are pinned above the organic results, at
 * most {@value #MAX_FEATURED_PER_PAGE} to a page, whatever the ordering.
 *
 * <p>Every tutor who matches the search appears exactly once across the pages. The featured
 * ones fill the top slots in strategy order - three on page one, the next three on page two,
 * and so on until they run out - and are left out of the organic list, which fills the rest of
 * each page from where the previous page stopped. So a page is always full, the total is the
 * plain number of matches, and paging forward never repeats or skips anybody.
 *
 * <p>The cap protects organic results, so it holds only while there are organic results left to
 * protect. Once they run out, featured tutors fill whole pages: holding them to three would leave
 * pages part-empty and put paid-for tutors on pages beyond the total the response reports.
 *
 * <p>Pure arithmetic, deliberately: this is the rule a paying tutor and a searching parent
 * both depend on, and it is tested here without a database.
 */
public final class FeaturedPinning {

    public static final int MAX_FEATURED_PER_PAGE = 3;

    private FeaturedPinning() {
    }

    /**
     * Which featured and which organic results belong on one page.
     *
     * @param page            zero-based page number
     * @param size            results per page
     * @param featuredMatches how many featured tutors match the search
     * @param totalMatches    how many tutors match the search, featured ones included
     */
    public static PageSlots slots(int page, int size, long featuredMatches, long totalMatches) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive, was " + size);
        }
        if (featuredMatches < 0 || featuredMatches > totalMatches) {
            throw new IllegalArgumentException("featuredMatches must be between 0 and totalMatches (%d), was %d"
                    .formatted(totalMatches, featuredMatches));
        }
        // A page of two cannot give three slots away.
        int perPage = Math.min(MAX_FEATURED_PER_PAGE, size);
        long featuredLeft = featuredMatches;
        long organicLeft = totalMatches - featuredMatches;
        long featuredOffset = 0;
        long organicOffset = 0;
        // Walk the pages before this one. A walk rather than a formula because the cap stops
        // applying part-way through, once organic results run out; page numbers are capped at
        // the request edge, so this is a few hundred additions at most.
        for (int current = 0; ; current++) {
            int featuredHere = (int) Math.min(featuredLeft, Math.max(perPage, size - Math.min(organicLeft, size)));
            if (current == page) {
                return new PageSlots(featuredOffset, featuredHere, organicOffset, size - featuredHere);
            }
            int organicHere = (int) Math.min(organicLeft, size - featuredHere);
            featuredOffset += featuredHere;
            featuredLeft -= featuredHere;
            organicOffset += organicHere;
            organicLeft -= organicHere;
            if (featuredLeft == 0 && organicLeft == 0) {
                // Past the last result: every later page is empty.
                return new PageSlots(featuredOffset, 0, organicOffset, size);
            }
        }
    }

    /**
     * Lays one page out: the featured results, flagged, above the organic ones. How many of each
     * belong on the page is {@link #slots}'s decision, not this method's.
     */
    public static <T> List<Placement<T>> pin(List<T> featured, List<T> organic) {
        List<Placement<T>> page = new ArrayList<>(featured.size() + organic.size());
        featured.forEach(item -> page.add(new Placement<>(item, true)));
        organic.forEach(item -> page.add(new Placement<>(item, false)));
        return List.copyOf(page);
    }

    /**
     * The two slices a page is assembled from.
     *
     * @param featuredOffset how many featured matches to skip, in strategy order
     * @param featuredLimit  how many featured matches this page shows
     * @param organicOffset  how many organic matches to skip, in strategy order
     * @param organicLimit   how many organic matches this page shows, at most
     */
    public record PageSlots(long featuredOffset, int featuredLimit, long organicOffset, int organicLimit) {
    }

    /** One result and whether it sits in a sponsored slot. */
    public record Placement<T>(T item, boolean featured) {
    }
}
