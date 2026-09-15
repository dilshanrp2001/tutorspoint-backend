package com.tutorspoint.search.ranking;

import java.util.List;

/**
 * One way of ordering search results (FR-S2, architecture section 7).
 *
 * <p>Strategy and Open/Closed together: a new ordering is a new {@code @Component}
 * implementing this, and {@link RankingStrategyFactory} picks it up by its {@link #key()}.
 * Nothing that selects or applies a strategy changes.
 *
 * <p>What a strategy does not decide:
 * <ul>
 *   <li><strong>Featured placement.</strong> Sponsored tutors are pinned above the organic
 *       results by {@link FeaturedPinning} whichever strategy is chosen, and are themselves
 *       ordered by that strategy - a strategy cannot opt out of the rule, and does not have to
 *       remember it.</li>
 *   <li><strong>The final tie-break.</strong> The repository appends the profile id to every
 *       ordering, so that pages never overlap or skip a tutor however many share a price.</li>
 * </ul>
 */
public interface RankingStrategy {

    /** The value of the {@code sort} query parameter that selects this strategy. Lower case. */
    String key();

    /** The ordering, most significant key first. Never empty. */
    List<SortKey> order(RankingContext context);
}
