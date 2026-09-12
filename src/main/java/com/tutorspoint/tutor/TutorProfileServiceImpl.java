package com.tutorspoint.tutor;

import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ReferenceEntity;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.Qualification;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.dto.QualificationRequest;
import com.tutorspoint.tutor.dto.TutorProfileDto;
import com.tutorspoint.tutor.dto.TutorProfileRequest;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The tutor profile use cases: draft, publish, unpublish, and one public read.
 *
 * <p>No business rule lives here. Whether a profile may be published, what a complete profile
 * is, whether a fee range makes sense - all of that is on {@link TutorProfile}, and this class
 * loads the profile, hands it what it asked for and lets it refuse. What does belong here is
 * everything the entity cannot know: who is calling, which reference rows a set of codes
 * stands for, and where the transaction begins and ends.
 *
 * <p>{@code @PreAuthorize} is per method rather than on the class, because one of the five is
 * public. The four owner-scoped methods carry the role gate with them, so a later caller that
 * is not an HTTP request is checked by the same rule as one that is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorProfileServiceImpl implements TutorProfileService {

    private final UserRepository users;
    private final TutorProfileRepository profiles;
    private final SubjectRepository subjects;
    private final ExamLevelRepository examLevels;
    private final SyllabusRepository syllabuses;
    private final AreaRepository areas;
    private final TutorMapper tutorMapper;
    private final ReferenceLabels referenceLabels;
    private final CurrentUser currentUser;

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public TutorProfileDto myProfile(Language language) {
        return toDto(requireOwnProfile(), language);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public TutorProfileDto updateMyProfile(TutorProfileRequest request, Language language) {
        TutorProfile profile = requireOwnProfile();

        // One call per wizard step. Each may refuse - an inverted fee range, a negative travel
        // radius - and refusing here rather than after a partial write is why the whole draft
        // arrives in one request.
        profile.describe(request.headline(), request.bio(), request.photoUrl(), request.introVideoUrl());
        profile.teaches(
                resolve(request.subjectCodes(), subjects::findByCodeInAndActiveTrue, "Subject"),
                resolve(request.examLevelCodes(), examLevels::findByCodeInAndActiveTrue, "Exam level"),
                resolve(request.syllabusCodes(), syllabuses::findByCodeInAndActiveTrue, "Syllabus"),
                request.mediums());
        profile.delivers(request.classFormats(), request.availableOnline(), request.availabilityStatus());
        profile.servesAreas(
                homeBase(request.homeBaseAreaCode()),
                resolve(request.areaServedCodes(), areas::findByCodeInAndActiveTrue, "Area"),
                request.travelRadiusKm());
        profile.listQualifications(toQualifications(request.qualifications()));
        profile.recordExperience(request.yearsOfExperience());
        profile.chargesBetween(request.feeMin(), request.feeMax(), request.feeUnit());

        log.info("Tutor {} saved profile draft ({} field(s) still missing)",
                profile.getTutor().getId(), profile.missingFields().size());
        return toDto(profile, language);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public TutorProfileDto publishMyProfile(Language language) {
        TutorProfile profile = requireOwnProfile();
        profile.publish();
        log.info("Tutor {} published their profile", profile.getTutor().getId());
        return toDto(profile, language);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public TutorProfileDto unpublishMyProfile(Language language) {
        TutorProfile profile = requireOwnProfile();
        profile.unpublish();
        log.info("Tutor {} unpublished their profile", profile.getTutor().getId());
        return toDto(profile, language);
    }

    /**
     * Open to guests, so there is no authorization to apply - only the status filter, which is
     * in the query rather than in a branch here.
     */
    @Override
    @Transactional(readOnly = true)
    public TutorProfileDto publicProfile(Long tutorId, Language language) {
        TutorProfile profile = profiles.findByTutorIdAndStatus(tutorId, ProfileStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor profile", tutorId));
        return toDto(profile, language);
    }

    /**
     * The caller's own profile, created empty on first use.
     *
     * <p>The tutor id comes from the token and nowhere else, which is what makes all four
     * owner-scoped methods above owner-scoped: no request names a profile, so there is nothing
     * for a caller to swap.
     */
    private TutorProfile requireOwnProfile() {
        Long callerId = currentUser.requireId();
        return profiles.findByTutorId(callerId)
                .orElseGet(() -> profiles.save(new TutorProfile(requireTutor(callerId))));
    }

    private Tutor requireTutor(Long callerId) {
        return users.findById(callerId)
                .filter(Tutor.class::isInstance)
                .map(Tutor.class::cast)
                // Unreachable through the HTTP routes, which the role rule already guards.
                // Kept so a future non-HTTP caller fails loudly instead of casting blindly.
                .orElseThrow(() -> new UnauthorizedActionException(
                        "Account %s is not a tutor account".formatted(callerId)));
    }

    /**
     * Turns the codes a client sent into the reference rows they name.
     *
     * <p>One query per list, not one per code, and an unknown code is an error rather than a
     * silently dropped selection: a tutor who picks Chemistry and gets a profile without it has
     * been lied to by the save button. The message names the offending codes so the client can
     * point at them.
     */
    private <T extends ReferenceEntity> Set<T> resolve(Set<String> codes,
                                                       Function<Collection<String>, List<T>> finder,
                                                       String type) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        List<T> found = finder.apply(codes);
        if (found.size() != codes.size()) {
            Set<String> unknown = new LinkedHashSet<>(codes);
            found.forEach(value -> unknown.remove(value.getCode()));
            throw new ResourceNotFoundException("%s codes not recognised: %s".formatted(type, unknown));
        }
        return new LinkedHashSet<>(found);
    }

    private Area homeBase(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return areas.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ResourceNotFoundException("Area", code));
    }

    private List<Qualification> toQualifications(List<QualificationRequest> requested) {
        if (requested == null) {
            return List.of();
        }
        // Constructed, not mapped: Qualification validates its own fields, and a MapStruct
        // mapper would have to bypass that constructor to build one.
        return requested.stream()
                .map(q -> new Qualification(q.title(), q.institution(), q.yearAwarded()))
                .toList();
    }

    private TutorProfileDto toDto(TutorProfile profile, Language language) {
        return tutorMapper.toProfileDto(profile, language, referenceLabels);
    }
}
