package com.tutorspoint.search;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.search.dto.FacetCount;
import com.tutorspoint.search.dto.SearchFacets;
import com.tutorspoint.search.dto.TutorSearchCriteria;
import com.tutorspoint.search.dto.TutorSearchHit;
import com.tutorspoint.search.dto.TutorSearchResponse;
import com.tutorspoint.search.featured.FeaturedTutorLookup;
import com.tutorspoint.search.ranking.FeaturedPinning;
import com.tutorspoint.search.ranking.FeaturedPinning.PageSlots;
import com.tutorspoint.search.ranking.GeoPoint;
import com.tutorspoint.search.ranking.RankingContext;
import com.tutorspoint.search.ranking.RankingStrategy;
import com.tutorspoint.search.ranking.RankingStrategyFactory;
import com.tutorspoint.search.ranking.SortKey;
import com.tutorspoint.search.repository.FacetDimension;
import com.tutorspoint.search.repository.TutorSearchRepository;
import com.tutorspoint.search.repository.TutorSearchSpecifications;
import com.tutorspoint.tutor.domain.TutorProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tutorspoint.search.repository.TutorSearchSpecifications.idIn;
import static com.tutorspoint.search.repository.TutorSearchSpecifications.idNotIn;

/**
 * Tutor search: filter, rank, pin, page, count.
 *
 * <p>The decisions each have one home, and none of them is here. What matches is
 * {@link TutorSearchSpecifications}; what order is the {@link RankingStrategy} the request named;
 * where sponsored results go is {@link FeaturedPinning}; who is sponsored is
 * {@link FeaturedTutorLookup}. This class puts them in sequence inside one read-only transaction.
 *
 * <p>No {@code @PreAuthorize}: search is public, because a parent compares tutors before
 * deciding whether to register at all. Visibility is enforced by the query, not by the caller's
 * role.
 */
@Service
@RequiredArgsConstructor
public class TutorSearchServiceImpl implements TutorSearchService {

    private final TutorSearchRepository searchRepository;
    private final AreaRepository areas;
    private final RankingStrategyFactory rankingStrategies;
    private final FeaturedTutorLookup featuredTutors;
    private final SearchMapper searchMapper;
    private final ReferenceLabels referenceLabels;

    @Override
    @Transactional(readOnly = true)
    public TutorSearchResponse search(TutorSearchCriteria criteria, Language language) {
        RankingStrategy strategy = rankingStrategies.resolve(criteria.sort());
        RankingContext context = new RankingContext(criteria.keyword(), originOf(criteria.area()));
        Specification<TutorProfile> matching = TutorSearchSpecifications.matching(criteria);

        long totalResults = searchRepository.count(matching);
        List<TutorSearchHit> results = totalResults == 0
                ? List.of()
                : rankedPage(criteria, matching, totalResults, strategy.order(context), context, language);

        return new TutorSearchResponse(results, criteria.page(), criteria.size(), totalResults,
                totalPages(totalResults, criteria.size()), strategy.key(), facets(criteria));
    }

    /**
     * Two ranked slices - the featured matches and the rest - cut to this page by the pinning
     * rule, then loaded and laid out. Both slices use the same ordering, which is what "featured
     * first within every strategy" means.
     */
    private List<TutorSearchHit> rankedPage(TutorSearchCriteria criteria, Specification<TutorProfile> matching,
                                            long totalResults, List<SortKey> order, RankingContext context,
                                            Language language) {
        Set<Long> featured = featuredTutors.featuredProfileIds();
        Specification<TutorProfile> featuredMatching = featured.isEmpty() ? null : matching.and(idIn(featured));
        Specification<TutorProfile> organicMatching = featured.isEmpty() ? matching : matching.and(idNotIn(featured));

        long featuredMatches = featuredMatching == null ? 0 : searchRepository.count(featuredMatching);
        PageSlots slots = FeaturedPinning.slots(criteria.page(), criteria.size(), featuredMatches, totalResults);

        List<Long> featuredIds = slots.featuredLimit() == 0
                ? List.of()
                : searchRepository.findRankedIds(featuredMatching, order, context,
                slots.featuredOffset(), slots.featuredLimit());
        List<Long> organicIds = searchRepository.findRankedIds(organicMatching, order, context,
                slots.organicOffset(), slots.organicLimit());

        Map<Long, TutorProfile> loaded = load(featuredIds, organicIds);
        return FeaturedPinning.pin(inRankOrder(featuredIds, loaded), inRankOrder(organicIds, loaded)).stream()
                .map(placement -> searchMapper.toHit(placement.item(), placement.featured(), language, referenceLabels))
                .toList();
    }

    private Map<Long, TutorProfile> load(List<Long> featuredIds, List<Long> organicIds) {
        List<Long> ids = new ArrayList<>(featuredIds);
        ids.addAll(organicIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return searchRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(TutorProfile::getId, Function.identity()));
    }

    /**
     * Loading by id forgets the ranking, so it is put back from the id list. A profile that
     * vanished between the two statements - unpublished a millisecond ago - is simply left out.
     */
    private static List<TutorProfile> inRankOrder(List<Long> ids, Map<Long, TutorProfile> loaded) {
        return ids.stream().map(loaded::get).filter(Objects::nonNull).toList();
    }

    /** Facets are counted even for a search with no results: they are how a parent gets out of one. */
    private SearchFacets facets(TutorSearchCriteria criteria) {
        return new SearchFacets(
                facet(FacetDimension.SUBJECT, criteria),
                facet(FacetDimension.EXAM_LEVEL, criteria),
                facet(FacetDimension.SYLLABUS, criteria),
                facet(FacetDimension.MEDIUM, criteria),
                facet(FacetDimension.CLASS_FORMAT, criteria),
                facet(FacetDimension.AREA, criteria));
    }

    private List<FacetCount> facet(FacetDimension dimension, TutorSearchCriteria criteria) {
        return searchMapper.toFacetCounts(
                searchRepository.countByFacet(dimension, TutorSearchSpecifications.matching(criteria, dimension)));
    }

    /**
     * The centre of the chosen area, for distance sorting. Absent when no area was chosen or
     * the code names none - and in the second case the area filter matches nobody anyway.
     */
    private GeoPoint originOf(String areaCode) {
        if (areaCode == null) {
            return null;
        }
        return areas.findByCode(areaCode)
                .map(area -> new GeoPoint(area.getLatitude().doubleValue(), area.getLongitude().doubleValue()))
                .orElse(null);
    }

    private static int totalPages(long totalResults, int size) {
        return Math.toIntExact((totalResults + size - 1) / size);
    }
}
