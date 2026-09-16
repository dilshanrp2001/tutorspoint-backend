package com.tutorspoint.enquiry;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.domain.Shortlist;
import com.tutorspoint.enquiry.dto.ShortlistRequest;
import com.tutorspoint.enquiry.dto.ShortlistResponse;
import com.tutorspoint.enquiry.repository.ShortlistRepository;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * A parent's saved tutors, owner-scoped by construction.
 *
 * <p>{@code hasRole('PARENT')} on the class is the role gate; the owner gate is the repository,
 * whose finders all take the parent id. Nothing here compares an owner by hand, so there is no
 * comparison to forget.
 *
 * <p>No masking rule appears in this class, and that is not an omission: a shortlist entry
 * carries a public tutor card, which has never held a contact detail and never will. Saving a
 * tutor is not contacting one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
public class ShortlistServiceImpl implements ShortlistService {

    private final ShortlistRepository shortlists;
    private final UserRepository users;
    private final TutorProfileRepository tutorProfiles;
    private final EnquiryMapper enquiryMapper;
    private final ReferenceLabels labels;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public List<ShortlistResponse> myShortlist(Language language) {
        List<Shortlist> saved = shortlists.findByParentIdOrderByCreatedAtDesc(currentUser.requireId());
        if (saved.isEmpty()) {
            return List.of();
        }
        // One query for every card on the page rather than one per row. A shortlist is short,
        // but "short" is not a number anybody promised, and an N+1 that only hurts the most
        // engaged parents is the worst kind to ship.
        Map<Long, TutorProfile> profiles = tutorProfiles
                .findByTutorIdInAndStatus(saved.stream().map(entry -> entry.getTutor().getId()).toList(),
                        ProfileStatus.PUBLISHED)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getTutor().getId(), Function.identity()));

        return saved.stream()
                // A tutor who has unpublished since is absent from the map, and the entry maps
                // with a null card. The row survives, so the parent sees what became of a tutor
                // they saved instead of finding their list quietly one shorter.
                .map(entry -> enquiryMapper.toShortlistResponse(
                        entry, profiles.get(entry.getTutor().getId()), language, labels))
                .toList();
    }

    @Override
    @Transactional
    public ShortlistResponse save(Long tutorId, ShortlistRequest request, Language language) {
        Long parentId = currentUser.requireId();
        TutorProfile profile = requireListedProfile(tutorId);

        Shortlist entry = shortlists.findByParentIdAndTutorId(parentId, tutorId)
                .map(existing -> {
                    existing.changeNote(request.note());
                    return existing;
                })
                .orElseGet(() -> shortlists.save(new Shortlist(requireParent(parentId), profile.getTutor(),
                        request.note())));

        log.info("Parent {} shortlisted tutor {}", parentId, tutorId);
        return enquiryMapper.toShortlistResponse(entry, profile, language, labels);
    }

    @Override
    @Transactional
    public void remove(Long tutorId) {
        shortlists.deleteByParentIdAndTutorId(currentUser.requireId(), tutorId);
        log.info("Parent {} removed tutor {} from their shortlist", currentUser.requireId(), tutorId);
    }

    /**
     * The tutor's published profile.
     *
     * <p>Published, not merely existing: a shortlist is built from listings, and there is no
     * route by which a parent could have seen an unpublished tutor to save them. A tutor who
     * unpublishes later keeps the entries already made — this rule is about adding one.
     */
    private TutorProfile requireListedProfile(Long tutorId) {
        return tutorProfiles.findByTutorIdAndStatus(tutorId, ProfileStatus.PUBLISHED)
                .filter(profile -> profile.getTutor().getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor", tutorId));
    }

    private Parent requireParent(Long parentId) {
        return users.findById(parentId)
                .filter(Parent.class::isInstance)
                .map(Parent.class::cast)
                .orElseThrow(() -> new ResourceNotFoundException("Parent account", parentId));
    }
}
