package com.tutorspoint.search.ranking;

import com.tutorspoint.search.ranking.FeaturedPinning.PageSlots;
import com.tutorspoint.search.ranking.FeaturedPinning.Placement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The sponsored-slot rule. The page arithmetic is checked case by case, and then as a property:
 * paging all the way through any mix of featured and organic results shows every tutor exactly
 * once, fills every page but the last, and gives a page more than three sponsored slots only
 * when no organic result is left to be pushed down by them.
 */
class FeaturedPinningTest {

    @Test
    @DisplayName("with nobody featured, a page is plain offset paging")
    void noFeatured() {
        assertThat(FeaturedPinning.slots(0, 20, 0, 100)).isEqualTo(new PageSlots(0, 0, 0, 20));
        assertThat(FeaturedPinning.slots(3, 20, 0, 100)).isEqualTo(new PageSlots(0, 0, 60, 20));
    }

    @Test
    @DisplayName("fewer than three featured all go on the first page, and organic results fill the rest")
    void fewFeatured() {
        assertThat(FeaturedPinning.slots(0, 20, 2, 100)).isEqualTo(new PageSlots(0, 2, 0, 18));
        // Page two starts where page one's organic results stopped.
        assertThat(FeaturedPinning.slots(1, 20, 2, 100)).isEqualTo(new PageSlots(2, 0, 18, 20));
    }

    @Test
    @DisplayName("at most three featured to a page; the rest take the slots on the following pages")
    void capAndCarryOver() {
        assertThat(FeaturedPinning.slots(0, 20, 7, 100)).isEqualTo(new PageSlots(0, 3, 0, 17));
        assertThat(FeaturedPinning.slots(1, 20, 7, 100)).isEqualTo(new PageSlots(3, 3, 17, 17));
        assertThat(FeaturedPinning.slots(2, 20, 7, 100)).isEqualTo(new PageSlots(6, 1, 34, 19));
        assertThat(FeaturedPinning.slots(3, 20, 7, 100)).isEqualTo(new PageSlots(7, 0, 53, 20));
    }

    @Test
    @DisplayName("once organic results run out, featured results fill the page instead of leaving it short")
    void featuredFillWhenOrganicRunsOut() {
        // 25 matches, 10 featured, pages of 10: 3+7, 3+7, then the last 4 featured and 1 organic.
        assertThat(FeaturedPinning.slots(0, 10, 10, 25)).isEqualTo(new PageSlots(0, 3, 0, 7));
        assertThat(FeaturedPinning.slots(1, 10, 10, 25)).isEqualTo(new PageSlots(3, 3, 7, 7));
        assertThat(FeaturedPinning.slots(2, 10, 10, 25)).isEqualTo(new PageSlots(6, 4, 14, 6));
        // Everybody featured: whole pages of them.
        assertThat(FeaturedPinning.slots(0, 20, 45, 45)).isEqualTo(new PageSlots(0, 20, 0, 0));
        assertThat(FeaturedPinning.slots(2, 20, 45, 45)).isEqualTo(new PageSlots(40, 5, 0, 15));
    }

    @Test
    @DisplayName("past the last result, a page is empty")
    void beyondTheEnd() {
        assertThat(FeaturedPinning.slots(9, 10, 4, 25)).isEqualTo(new PageSlots(4, 0, 21, 10));
    }

    @Test
    @DisplayName("a page smaller than three gives every slot to featured results until they run out")
    void tinyPages() {
        assertThat(FeaturedPinning.slots(0, 2, 5, 20)).isEqualTo(new PageSlots(0, 2, 0, 0));
        assertThat(FeaturedPinning.slots(2, 2, 5, 20)).isEqualTo(new PageSlots(4, 1, 0, 1));
        assertThat(FeaturedPinning.slots(3, 2, 5, 20)).isEqualTo(new PageSlots(5, 0, 1, 2));
    }

    @ParameterizedTest(name = "{0} matches, {1} featured, pages of {2}")
    @CsvSource({
            "0, 0, 20",
            "5, 0, 2",
            "5, 5, 2",
            "45, 2, 20",
            "45, 7, 20",
            "45, 45, 20",
            "10, 4, 3",
            "100, 13, 7",
            "25, 10, 10",
            "12, 11, 5",
            "3, 3, 1"})
    @DisplayName("paging through every result shows each tutor exactly once, featured first on each page")
    void pagesPartitionEveryResult(int total, int featured, int size) {
        List<String> featuredTutors = IntStream.range(0, featured).mapToObj(i -> "F" + i).toList();
        List<String> organicTutors = IntStream.range(0, total - featured).mapToObj(i -> "O" + i).toList();

        List<String> seen = new ArrayList<>();
        int pages = (total + size - 1) / size;
        for (int page = 0; page < pages + 1; page++) {
            PageSlots slots = FeaturedPinning.slots(page, size, featured, total);
            List<Placement<String>> laidOut = FeaturedPinning.pin(
                    slice(featuredTutors, slots.featuredOffset(), slots.featuredLimit()),
                    slice(organicTutors, slots.organicOffset(), slots.organicLimit()));
            laidOut.forEach(placement -> seen.add(placement.item()));

            long featuredOnPage = laidOut.stream().filter(Placement::featured).count();
            if (featuredOnPage > FeaturedPinning.MAX_FEATURED_PER_PAGE) {
                assertThat(seen.stream().filter(item -> item.startsWith("O")))
                        .as("page %d exceeds the cap only once organic results are exhausted", page)
                        .hasSize(organicTutors.size());
            }
            // Every sponsored result is above every organic one.
            List<Boolean> flags = laidOut.stream().map(Placement::featured).toList();
            assertThat(flags).isSortedAccordingTo((a, b) -> Boolean.compare(b, a));
            if (page < pages - 1) {
                assertThat(laidOut).as("page %d is full", page).hasSize(size);
            }
            if (page >= pages) {
                assertThat(laidOut).as("page %d is past the end", page).isEmpty();
            }
        }

        List<String> everyone = new ArrayList<>(featuredTutors);
        everyone.addAll(organicTutors);
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(everyone);
        // And in order: featured in their ranking, organic in theirs.
        assertThat(seen.stream().filter(item -> item.startsWith("F")).toList()).isEqualTo(featuredTutors);
        assertThat(seen.stream().filter(item -> item.startsWith("O")).toList()).isEqualTo(organicTutors);
    }

    @Test
    @DisplayName("pin flags featured results and puts them first")
    void pinLaysOutAPage() {
        assertThat(FeaturedPinning.pin(List.of("a", "b"), List.of("c"))).containsExactly(
                new Placement<>("a", true), new Placement<>("b", true), new Placement<>("c", false));
        assertThat(FeaturedPinning.pin(List.of(), List.of("c"))).containsExactly(new Placement<>("c", false));
    }

    @Test
    @DisplayName("rejects impossible paging")
    void rejectsImpossibleInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeaturedPinning.slots(-1, 20, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> FeaturedPinning.slots(0, 0, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> FeaturedPinning.slots(0, 20, -1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> FeaturedPinning.slots(0, 20, 11, 10));
    }

    private static List<String> slice(List<String> items, long offset, int limit) {
        int from = (int) Math.min(offset, items.size());
        return items.subList(from, Math.min(from + limit, items.size()));
    }
}
