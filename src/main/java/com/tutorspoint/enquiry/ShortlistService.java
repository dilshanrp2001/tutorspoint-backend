package com.tutorspoint.enquiry;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.dto.ShortlistRequest;
import com.tutorspoint.enquiry.dto.ShortlistResponse;

import java.util.List;

/**
 * The tutors a parent has saved to come back to (FR-P1).
 *
 * <p>Like {@code ChildProfileService}, no signature carries an owner: the parent is always the
 * caller, and every entry is reached through a query that already filters by them.
 */
public interface ShortlistService {

    /** The whole shortlist, most recently saved first, each with the tutor's public card. */
    List<ShortlistResponse> myShortlist(Language language);

    /**
     * Saves a tutor, or rewrites the note on one already saved.
     *
     * <p>Idempotent by design. The pair is unique, and a parent who saves the same tutor twice
     * has changed their mind about the note rather than created a second entry — which is also
     * what makes the button safe to double-click.
     *
     * @throws ResourceNotFoundException if the tutor has no published profile
     */
    ShortlistResponse save(Long tutorId, ShortlistRequest request, Language language);

    /**
     * Removes a tutor from the shortlist. Removing one that was never saved is not an error:
     * the caller wanted them gone, and they are.
     */
    void remove(Long tutorId);
}
