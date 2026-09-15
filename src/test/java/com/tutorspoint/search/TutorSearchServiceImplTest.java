package com.tutorspoint.search;

import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.search.dto.TutorSearchCriteria;
import com.tutorspoint.search.dto.TutorSearchHit;
import com.tutorspoint.search.dto.TutorSearchResponse;
import com.tutorspoint.search.featured.FeaturedTutorLookup;
import com.tutorspoint.search.ranking.DistanceRanking;
import com.tutorspoint.search.ranking.ExperienceRanking;
import com.tutorspoint.search.ranking.GeoPoint;
import com.tutorspoint.search.ranking.PriceRanking;
import com.tutorspoint.search.ranking.RankingContext;
import com.tutorspoint.search.ranking.RankingStrategyFactory;
import com.tutorspoint.search.ranking.RatingRanking;
import com.tutorspoint.search.ranking.RelevanceRanking;
import com.tutorspoint.search.ranking.SortKey;
import com.tutorspoint.search.repository.FacetDimension;
import com.tutorspoint.search.repository.TutorSearchRepository;
import com.tutorspoint.tutor.TutorMapperImpl;
import com.tutorspoint.tutor.domain.TutorProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sequence around the rules: which strategy, which slices, which ids loaded, in what order
 * the page comes back. The rules themselves are tested where they live - matching in
 * {@code TutorSearchIT}, ordering in each strategy's test, slot arithmetic in
 * {@code FeaturedPinningTest}.
 *
 * <p>The factory, the strategies and both mappers are real: they have no collaborators, and
 * stubbing them would leave nothing to say about what a caller receives. The repository is
 * mocked, and its two ranked-id calls are told apart by order - featured slice first, organic
 * second - which is part of what is being verified.
 */
@ExtendWith(MockitoExtension.class)
class TutorSearchServiceImplTest {

    @Mock
    private TutorSearchRepository searchRepository;

    @Mock
    private AreaRepository areas;

    @Mock
    private FeaturedTutorLookup featuredTutors;

    @Mock
    private ReferenceLabels referenceLabels;

    private final RankingStrategyFactory strategies = new RankingStrategyFactory(List.of(
            new RelevanceRanking(), new PriceRanking(), new RatingRanking(), new ExperienceRanking(), new DistanceRanking()));

    private TutorSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TutorSearchServiceImpl(searchRepository, areas, strategies, featuredTutors,
                new SearchMapperImpl(new TutorMapperImpl()), referenceLabels);
        lenient().when(referenceLabels.mediums(anyCollection(), any())).thenReturn(List.of());
        lenient().when(featuredTutors.featuredProfileIds()).thenReturn(Set.of());
        lenient().when(searchRepository.countByFacet(any(), any())).thenReturn(Map.of());
    }

    @ParameterizedTest(name = "sort={0}")
    @ValueSource(strings = {"relevance", "price_asc", "rating_desc", "experience_desc", "distance_asc"})
    @DisplayName("within every strategy, featured matches are pinned above organic ones and both are ranked by it")
    void featuredArePinnedWithinEveryStrategy(String sort) {
        stubArea("COLOMBO", 6.9271, 79.8612);
        when(featuredTutors.featuredProfileIds()).thenReturn(Set.of(1L, 2L));
        when(searchRepository.count(any(Specification.class))).thenReturn(5L, 2L);
        when(searchRepository.findRankedIds(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(2L, 1L), List.of(5L, 3L, 4L));
        stubProfiles(1L, 2L, 3L, 4L, 5L);

        TutorSearchResponse response = service.search(criteria(sort, "COLOMBO", 0, 20), Language.EN);

        assertThat(response.results()).extracting(hit -> hit.tutor().tutorId()).containsExactly(102L, 101L, 105L, 103L, 104L);
        assertThat(response.results()).extracting(TutorSearchHit::featured).containsExactly(true, true, false, false, false);
        assertThat(response.sort()).isEqualTo(sort);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SortKey>> orders = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Long> offsets = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limits = ArgumentCaptor.forClass(Integer.class);
        verify(searchRepository, times(2)).findRankedIds(any(), orders.capture(), any(), offsets.capture(), limits.capture());

        List<SortKey> expected = strategies.resolve(sort).order(new RankingContext(null, new GeoPoint(6.9271, 79.8612)));
        assertThat(orders.getAllValues()).containsOnly(expected);
        // Two featured slots, then eighteen organic places.
        assertThat(offsets.getAllValues()).containsExactly(0L, 0L);
        assertThat(limits.getAllValues()).containsExactly(2, 18);
    }

    @Test
    @DisplayName("with nobody featured there is one ranked slice, the whole page, and nothing is flagged")
    void noFeatured() {
        when(searchRepository.count(any(Specification.class))).thenReturn(3L);
        when(searchRepository.findRankedIds(any(), any(), any(), eq(0L), eq(20))).thenReturn(List.of(3L, 1L, 2L));
        stubProfiles(1L, 2L, 3L);

        TutorSearchResponse response = service.search(TutorSearchCriteria.unfiltered(), Language.EN);

        assertThat(response.results()).extracting(hit -> hit.tutor().tutorId()).containsExactly(103L, 101L, 102L);
        assertThat(response.results()).noneMatch(TutorSearchHit::featured);
        assertThat(response.sort()).isEqualTo("relevance");
        verify(searchRepository, times(1)).count(any(Specification.class));
        verify(searchRepository, times(1)).findRankedIds(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("a later page asks for the slices the pinning rule gives it")
    void laterPagesUseShiftedSlices() {
        when(featuredTutors.featuredProfileIds()).thenReturn(Set.of(1L, 2L, 3L, 4L, 5L));
        when(searchRepository.count(any(Specification.class))).thenReturn(30L, 5L);
        when(searchRepository.findRankedIds(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(4L, 5L), List.of(6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L));
        stubProfiles(4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L);

        TutorSearchResponse response = service.search(criteria(null, null, 1, 10), Language.EN);

        ArgumentCaptor<Long> offsets = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limits = ArgumentCaptor.forClass(Integer.class);
        verify(searchRepository, times(2)).findRankedIds(any(), any(), any(), offsets.capture(), limits.capture());
        // Page one took three featured and seven organic; page two takes the last two featured
        // and eight organic, starting from the eighth.
        assertThat(offsets.getAllValues()).containsExactly(3L, 7L);
        assertThat(limits.getAllValues()).containsExactly(2, 8);
        assertThat(response.results()).hasSize(10);
        assertThat(response.totalResults()).isEqualTo(30);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("no matches means no ranking queries and no loading, but facets are still counted")
    void emptyResult() {
        when(searchRepository.count(any(Specification.class))).thenReturn(0L);
        when(searchRepository.countByFacet(eq(FacetDimension.SUBJECT), any())).thenReturn(Map.of("ICT", 2L, "CHEMISTRY", 4L));

        TutorSearchResponse response = service.search(TutorSearchCriteria.unfiltered(), Language.EN);

        assertThat(response.results()).isEmpty();
        assertThat(response.totalPages()).isZero();
        verify(searchRepository, never()).findRankedIds(any(), any(), any(), anyLong(), anyInt());
        verify(searchRepository, never()).findByIdIn(any());
        verify(featuredTutors, never()).featuredProfileIds();
        // Largest first.
        assertThat(response.facets().subjects()).extracting("code").containsExactly("CHEMISTRY", "ICT");
        for (FacetDimension dimension : FacetDimension.values()) {
            verify(searchRepository).countByFacet(eq(dimension), any());
        }
    }

    @Test
    @DisplayName("the chosen area's centre is the ranking origin; no area, no origin")
    void originComesFromTheArea() {
        stubArea("COLOMBO_NUGEGODA", 6.8649, 79.8997);
        when(searchRepository.count(any(Specification.class))).thenReturn(1L, 1L);
        when(searchRepository.findRankedIds(any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.search(criteria("distance_asc", "colombo_nugegoda", 0, 20), Language.EN);
        service.search(criteria("distance_asc", null, 0, 20), Language.EN);

        ArgumentCaptor<RankingContext> contexts = ArgumentCaptor.forClass(RankingContext.class);
        verify(searchRepository, times(2)).findRankedIds(any(), any(), contexts.capture(), anyLong(), anyInt());
        assertThat(contexts.getAllValues().get(0).origin()).isEqualTo(new GeoPoint(6.8649, 79.8997));
        assertThat(contexts.getAllValues().get(1).origin()).isNull();
    }

    @Test
    @DisplayName("a profile that disappears between ranking and loading is left out, not an error")
    void vanishedProfileIsSkipped() {
        when(searchRepository.count(any(Specification.class))).thenReturn(3L);
        when(searchRepository.findRankedIds(any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        stubProfiles(1L, 3L);

        TutorSearchResponse response = service.search(TutorSearchCriteria.unfiltered(), Language.EN);

        assertThat(response.results()).extracting(hit -> hit.tutor().tutorId()).containsExactly(101L, 103L);
    }

    // ---------------------------------------------------------------------

    private void stubArea(String code, double latitude, double longitude) {
        Area area = Area.district(code, 10, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
        when(areas.findByCode(code)).thenReturn(Optional.of(area));
    }

    /** Profile n belongs to tutor 100 + n, so the two ids cannot be confused in an assertion. */
    private void stubProfiles(Long... profileIds) {
        List<TutorProfile> loaded = Arrays.stream(profileIds).map(id -> {
            Tutor tutor = new Tutor("tutor" + id + "@example.lk", "hash", "Tutor " + id, "+9477000000" + id, Language.EN);
            ReflectionTestUtils.setField(tutor, "id", 100 + id);
            TutorProfile profile = new TutorProfile(tutor);
            ReflectionTestUtils.setField(profile, "id", id);
            return profile;
        }).toList();
        when(searchRepository.findByIdIn(any())).thenReturn(loaded);
    }

    private static TutorSearchCriteria criteria(String sort, String area, int page, int size) {
        return new TutorSearchCriteria(null, null, null, null, null, area, null, null,
                null, null, null, null, sort, page, size);
    }
}
