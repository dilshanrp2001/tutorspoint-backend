package com.tutorspoint.enquiry.repository;

import com.tutorspoint.enquiry.domain.Shortlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * A seeker's saved tutors (FR-P1).
 *
 * <p>Owner-scoped like {@code ChildProfileRepository}: every finder takes the seeker id, so
 * nothing above this layer has an owner comparison to forget.
 */
public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    /**
     * The whole shortlist, most recently saved first. A shortlist is a handful of tutors by
     * definition, so it is read in one go rather than paged.
     */
    @EntityGraph(attributePaths = {"tutor"})
    List<Shortlist> findBySeekerIdOrderByCreatedAtDesc(Long seekerId);

    @EntityGraph(attributePaths = {"tutor"})
    Optional<Shortlist> findBySeekerIdAndTutorId(Long seekerId, Long tutorId);

    /** Removing a tutor the seeker never saved is not an error; the delete simply affects nothing. */
    void deleteBySeekerIdAndTutorId(Long seekerId, Long tutorId);

    boolean existsBySeekerIdAndTutorId(Long seekerId, Long tutorId);
}
