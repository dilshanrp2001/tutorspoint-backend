package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminDocumentResponse;
import com.tutorspoint.admin.dto.AdminTutorDetail;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;

/**
 * Reviewing a tutor: their documents, their verified badge, and their profile's standing.
 * Administrators only.
 *
 * <p>The minimal pilot version of Phase 6's verification workflow. An administrator reads the
 * documents, rules on each, and sets the badge by hand; nothing here derives the badge from the
 * document decisions, because concierge-onboarded pilot tutors are verified in person.
 */
public interface AdminTutorService {

    /**
     * The review screen for one tutor.
     *
     * @throws ResourceNotFoundException if there is no tutor account with this id
     */
    AdminTutorDetail tutor(Long tutorId);

    /**
     * @throws ResourceNotFoundException      if there is no such document
     * @throws BusinessRuleViolationException if it has already been ruled on
     */
    AdminDocumentResponse approveDocument(Long documentId, String notes);

    /**
     * @param notes the reason, shown to the tutor; required
     * @throws ResourceNotFoundException      if there is no such document
     * @throws BusinessRuleViolationException if it has already been ruled on
     */
    AdminDocumentResponse rejectDocument(Long documentId, String notes);

    /**
     * Grants or withdraws the verified badge. Idempotent: asking for the state it is already in
     * changes nothing and records nothing.
     *
     * @throws ResourceNotFoundException if the tutor has no profile to carry a badge
     */
    AdminTutorDetail setVerified(Long tutorId, boolean verified);

    /**
     * Takes the profile down and freezes it. Idempotent for a profile already suspended.
     *
     * @throws ResourceNotFoundException if the tutor has no profile
     */
    AdminTutorDetail suspendProfile(Long tutorId, String reason);

    /**
     * Lifts a profile suspension, handing the profile back to the tutor as a draft to republish.
     *
     * @throws ResourceNotFoundException      if the tutor has no profile
     * @throws BusinessRuleViolationException if the profile is not suspended
     */
    AdminTutorDetail reinstateProfile(Long tutorId, String reason);
}
