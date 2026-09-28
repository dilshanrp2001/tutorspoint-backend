package com.tutorspoint.search.ranking;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the {@code sort} query parameter into the strategy it names.
 *
 * <p>Every {@link RankingStrategy} bean is registered by its key, so this class never names a
 * strategy other than the default. Two strategies claiming one key is a programming error, and
 * it fails the application at startup rather than letting whichever loaded last win.
 */
@Component
public class RankingStrategyFactory {

    private final Map<String, RankingStrategy> byKey;

    public RankingStrategyFactory(List<RankingStrategy> strategies) {
        this.byKey = Collections.unmodifiableMap(strategies.stream().collect(Collectors.toMap(
                strategy -> normalise(strategy.key()),
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Two ranking strategies share the key '%s': %s and %s"
                            .formatted(first.key(), first.getClass().getSimpleName(),
                                    second.getClass().getSimpleName()));
                },
                TreeMap::new)));
        if (!byKey.containsKey(RelevanceRanking.KEY)) {
            throw new IllegalStateException("The default ranking strategy '%s' is not registered"
                    .formatted(RelevanceRanking.KEY));
        }
    }

    /**
     * The strategy for a sort parameter; relevance when none was given.
     *
     * @throws IllegalArgumentException for a key no strategy answers to. The request DTO
     *                                  rejects those first, so reaching this is a bug.
     */
    public RankingStrategy resolve(String sort) {
        if (sort == null || sort.isBlank()) {
            return byKey.get(RelevanceRanking.KEY);
        }
        RankingStrategy strategy = byKey.get(normalise(sort));
        if (strategy == null) {
            throw new IllegalArgumentException("No ranking strategy for sort '%s'; expected one of %s"
                    .formatted(sort, byKey.keySet()));
        }
        return strategy;
    }

    /** Whether {@link #resolve} would accept this value. Blank counts: it means the default. */
    public boolean supports(String sort) {
        return sort == null || sort.isBlank() || byKey.containsKey(normalise(sort));
    }

    /** Every accepted key, alphabetically - for error messages and the API documentation. */
    public Set<String> keys() {
        return byKey.keySet();
    }

    private static String normalise(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
