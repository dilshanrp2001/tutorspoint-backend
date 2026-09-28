package com.tutorspoint.search.featured;

import java.util.Set;

/**
 * Which tutor profiles are featured right now (FR-S6).
 *
 * <p>Search asks this and nothing else. Today the answer comes from
 * {@link PlaceholderFeaturedTutorLookup}; Phase 10 answers it from paid
 * {@code FeaturedPlacement} records instead, by replacing that one bean. The pinning rule, the
 * ranking and the response shape are already in place and do not change.
 */
public interface FeaturedTutorLookup {

    /** The ids of the tutor profiles holding an active featured placement. Never null. */
    Set<Long> featuredProfileIds();
}
