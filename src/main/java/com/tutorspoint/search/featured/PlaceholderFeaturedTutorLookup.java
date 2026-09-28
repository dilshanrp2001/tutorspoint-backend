package com.tutorspoint.search.featured;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Nobody is featured until featured placement can be bought (Phase 10).
 *
 * <p>An honest empty answer rather than a fake one: promoting some arbitrary tutor into a
 * sponsored slot nobody paid for would be a lie on the results page. The sponsored slot is
 * still real - results carry their {@code featured} flag, and the pinning rule runs on every
 * search - so Phase 10 changes what this returns and nothing else. Delete this class when the
 * real implementation arrives.
 */
@Component
public class PlaceholderFeaturedTutorLookup implements FeaturedTutorLookup {

    @Override
    public Set<Long> featuredProfileIds() {
        return Set.of();
    }
}
