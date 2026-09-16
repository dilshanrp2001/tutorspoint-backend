package com.tutorspoint.enquiry.repository;

import com.tutorspoint.enquiry.domain.Shortlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * A parent's saved tutors (FR-P1).
 *
 * <p>Owner-scoped like {@code ChildProfileRepository}: every finder takes the parent id, so
 * nothing above this layer has an owner comparison to forget.
 */
public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    /**
     * The whole shortlist, most recently saved first. A shortlist is a handful of tutors by
     * definition, so it is read in one go rather than paged.
     */
    @EntityGraph(attributePaths = {"tutor"})
    List<Shortlist> findByParentIdOrderByCreatedAtDesc(Long parentId);

    @EntityGraph(attributePaths = {"tutor"})
    Optional<Shortlist> findByParentIdAndTutorId(Long parentId, Long tutorId);

    /** Removing a tutor the parent never saved is not an error; the delete simply affects nothing. */
    void deleteByParentIdAndTutorId(Long parentId, Long tutorId);

    boolean existsByParentIdAndTutorId(Long parentId, Long tutorId);
}
