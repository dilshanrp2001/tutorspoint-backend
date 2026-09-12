package com.tutorspoint.tutor;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.tutor.dto.TutorProfileDto;
import com.tutorspoint.tutor.dto.TutorProfileRequest;

/**
 * What a tutor may do to their own profile, and what everybody else may see of it
 * (FR-T1 - FR-T8).
 *
 * <p>The owner-scoped methods take no tutor id. The subject of each is the caller, so there is
 * no parameter to point at somebody else - the same shape {@code AccountService} uses, and the
 * strongest form the ownership rule can take. {@link #publicProfile} is the one method that
 * names a tutor, and it can only ever answer with a published profile.
 *
 * <p>Every method takes the caller's language, because a profile is largely made of reference
 * values and those have to come back in words the caller reads. Resolving a header into a
 * {@link Language} is the controller's job; nothing below it touches HTTP.
 */
public interface TutorProfileService {

    /**
     * The caller's own profile, in whatever state it is in.
     *
     * <p>Creates an empty draft the first time a tutor asks for one, so the wizard always has a
     * profile to render and a {@code missingFields} list to draw its progress bar from. That
     * makes this a read that can write, which is a deliberate trade: the alternative is a 404
     * that every client has to special-case into an empty form.
     */
    TutorProfileDto myProfile(Language language);

    /**
     * Saves the whole draft (FR-T8). Never publishes: a tutor edits in private and goes live
     * on purpose, so a published profile that is edited stays published and a draft stays a
     * draft.
     *
     * @throws ResourceNotFoundException      if a subject, level, syllabus or area code in the
     *                                        request is not an active reference value
     * @throws BusinessRuleViolationException if the profile has been suspended by an
     *                                        administrator
     */
    TutorProfileDto updateMyProfile(TutorProfileRequest request, Language language);

    /**
     * Makes the caller's profile public (FR-T8).
     *
     * @throws BusinessRuleViolationException if the profile is incomplete - the response names
     *                                        the fields, and {@link #myProfile} lists them
     */
    TutorProfileDto publishMyProfile(Language language);

    /**
     * Takes the caller's profile back off the platform (FR-T8). The content is kept.
     *
     * @throws BusinessRuleViolationException if the profile is not currently published
     */
    TutorProfileDto unpublishMyProfile(Language language);

    /**
     * A tutor profile as the public sees it (FR-S4). Open to guests: a parent compares tutors
     * before deciding whether to register.
     *
     * @throws ResourceNotFoundException if the tutor has no profile, or has one that is not
     *                                   published - the same answer either way, so the
     *                                   endpoint cannot be used to discover who is drafting
     */
    TutorProfileDto publicProfile(Long tutorId, Language language);
}
