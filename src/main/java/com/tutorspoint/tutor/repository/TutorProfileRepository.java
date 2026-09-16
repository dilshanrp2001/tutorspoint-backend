package com.tutorspoint.tutor.repository;

import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Tutor profiles, reached by their owner rather than by their own id.
 *
 * <p>Every finder here takes the tutor id, because that is the only id a caller ever has: the
 * owner knows their account, and the public URL is addressed by the tutor, not by the profile
 * row behind it. The profile id never appears in the API at all.
 */
public interface TutorProfileRepository extends JpaRepository<TutorProfile, Long> {

    /** The owner view: whatever state the profile is in, including an untouched draft. */
    Optional<TutorProfile> findByTutorId(Long tutorId);

    /**
     * The public view.
     *
     * <p>The status is part of the query rather than a check on the result, which is what
     * makes a draft indistinguishable from a tutor who does not exist: there is no branch
     * where a found-but-unpublished profile could be returned by mistake.
     */
    Optional<TutorProfile> findByTutorIdAndStatus(Long tutorId, ProfileStatus status);

    /**
     * The public view for several tutors at once, for a screen that already holds a list of
     * tutor ids — a parent's shortlist (FR-P1) is the first of them.
     *
     * <p>The plural form exists so such a screen is one query rather than one per row. A tutor
     * whose profile is not published is simply absent from the result, exactly as
     * {@link #findByTutorIdAndStatus} leaves them absent, so the caller has no found-but-hidden
     * case to handle differently.
     */
    List<TutorProfile> findByTutorIdInAndStatus(Collection<Long> tutorIds, ProfileStatus status);

    /** For the metrics summary: how many profiles are in a state right now. */
    long countByStatus(ProfileStatus status);

    long countByStatusAndVerifiedTrue(ProfileStatus status);
}
