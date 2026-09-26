package com.tutorspoint.search.repository;

import com.tutorspoint.tutor.domain.TutorProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/**
 * Tutor profiles as search reads them. The tutor feature owns the profile and writes it; this
 * repository only filters, ranks, counts and loads.
 */
public interface TutorSearchRepository extends JpaRepository<TutorProfile, Long>,
        JpaSpecificationExecutor<TutorProfile>, TutorSearchQueries {

    /**
     * The profiles behind one page of ranked ids, in no particular order - the caller restores
     * the ranking. The account and home base come in the same statement; the card's subject,
     * exam level and medium lists load in batches (see {@code default_batch_fetch_size}), a few
     * queries for the whole page rather than a few per card.
     */
    @EntityGraph(attributePaths = {"tutor", "homeBaseArea"})
    List<TutorProfile> findByIdIn(Collection<Long> ids);
}
