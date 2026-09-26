package com.tutorspoint.search.repository;

import com.tutorspoint.search.ranking.RankingContext;
import com.tutorspoint.search.ranking.SortKey;
import com.tutorspoint.tutor.domain.TutorProfile;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

/**
 * The two search queries Spring Data cannot derive: an ordering built from ranking keys, and
 * a grouped count. Implemented in {@link TutorSearchQueriesImpl} and mixed into
 * {@link TutorSearchRepository}.
 */
public interface TutorSearchQueries {

    /**
     * The ids of one slice of matching profiles, in ranking order.
     *
     * <p>Ids rather than entities: a page is ranked and cut here, then its profiles are loaded
     * by id. Fetching the collections a card needs in the ranked query itself would multiply its
     * rows, and paging over multiplied rows either lies or happens in memory.
     *
     * @param order  most significant first; the profile id is always appended as a final
     *               tie-break, so slices of one ordering never overlap
     * @param offset matching profiles to skip
     * @param limit  at most this many ids; zero returns an empty list without a query
     */
    List<Long> findRankedIds(Specification<TutorProfile> matching, List<SortKey> order,
                             RankingContext context, long offset, int limit);

    /**
     * How many matching profiles carry each value of one many-valued attribute, keyed by the
     * value's code (or enum name). Values with no match are absent.
     */
    Map<String, Long> countByFacet(FacetDimension dimension, Specification<TutorProfile> matching);
}
