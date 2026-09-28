package com.tutorspoint.search.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RankingStrategyFactoryTest {

    private final RankingStrategyFactory factory = new RankingStrategyFactory(List.of(
            new RelevanceRanking(), new PriceRanking(), new RatingRanking(),
            new ExperienceRanking(), new DistanceRanking()));

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "relevance, RelevanceRanking",
            "price_asc, PriceRanking",
            "rating_desc, RatingRanking",
            "experience_desc, ExperienceRanking",
            "distance_asc, DistanceRanking",
            "PRICE_ASC, PriceRanking",
            "' rating_desc ', RatingRanking"})
    @DisplayName("resolves each sort key, ignoring case and surrounding space")
    void resolvesByKey(String sort, String expected) {
        assertThat(factory.resolve(sort).getClass().getSimpleName()).isEqualTo(expected);
        assertThat(factory.supports(sort)).isTrue();
    }

    @Test
    @DisplayName("no sort means relevance")
    void defaultsToRelevance() {
        assertThat(factory.resolve(null)).isInstanceOf(RelevanceRanking.class);
        assertThat(factory.resolve("  ")).isInstanceOf(RelevanceRanking.class);
        assertThat(factory.supports(null)).isTrue();
    }

    @Test
    @DisplayName("an unknown key is not supported, and resolving it fails loudly")
    void unknownKey() {
        assertThat(factory.supports("cheapest")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> factory.resolve("cheapest"));
    }

    @Test
    @DisplayName("lists every key it answers to")
    void keys() {
        assertThat(factory.keys())
                .containsExactly("distance_asc", "experience_desc", "price_asc", "rating_desc", "relevance");
    }

    @Test
    @DisplayName("a new strategy is picked up by its key with no change to the factory")
    void openForExtension() {
        RankingStrategy newest = new RankingStrategy() {
            @Override
            public String key() {
                return "newest";
            }

            @Override
            public List<SortKey> order(RankingContext context) {
                return List.of(SortKey.descending(RankField.VERIFIED));
            }
        };

        RankingStrategyFactory extended = new RankingStrategyFactory(List.of(new RelevanceRanking(), newest));

        assertThat(extended.resolve("newest")).isSameAs(newest);
    }

    @Test
    @DisplayName("two strategies claiming one key fail at startup instead of one silently winning")
    void duplicateKeysAreRejected() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new RankingStrategyFactory(List.of(new RelevanceRanking(), new RelevanceRanking())))
                .withMessageContaining("relevance");
    }

    @Test
    @DisplayName("the default strategy must be registered")
    void defaultIsRequired() {
        assertThatIllegalStateException().isThrownBy(() -> new RankingStrategyFactory(List.of(new PriceRanking())));
    }
}
