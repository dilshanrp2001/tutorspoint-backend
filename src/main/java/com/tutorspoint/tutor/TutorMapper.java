package com.tutorspoint.tutor;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.tutor.domain.ProfileField;
import com.tutorspoint.tutor.domain.Qualification;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.dto.QualificationDto;
import com.tutorspoint.tutor.dto.TutorCardDto;
import com.tutorspoint.tutor.dto.TutorProfileDto;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * The one place a tutor profile becomes a response DTO.
 *
 * <p>Two contexts travel with every call. {@link Language} is the caller's language, so a
 * reference value renders through {@code nameIn} with its English fallback rather than each
 * caller re-deciding what to do about a missing Tamil name. {@link ReferenceLabels} is the
 * message-bundle lookup for the two enum-backed lists, which have no translation rows of their
 * own - passing it in keeps that translation in the single home it already has instead of
 * field-injecting a {@code MessageSource} into a generated class.
 *
 * <p>Generated rather than hand-written, and the build sets
 * {@code unmappedTargetPolicy=ERROR}: a field added to either response DTO is a compile
 * failure until it is mapped, not a null that reaches a client.
 */
@Mapper
public interface TutorMapper {

    @Mapping(target = "tutorId", source = "profile.tutor.id")
    @Mapping(target = "fullName", source = "profile.tutor.fullName")
    @Mapping(target = "mediums", expression = "java(labels.mediums(profile.getMediums(), language))")
    @Mapping(target = "classFormats", expression = "java(labels.classFormats(profile.getClassFormats(), language))")
    @Mapping(target = "complete", expression = "java(profile.isComplete())")
    @Mapping(target = "missingFields", expression = "java(toFieldNames(profile.missingFields()))")
    TutorProfileDto toProfileDto(TutorProfile profile,
                                 @Context Language language,
                                 @Context ReferenceLabels labels);

    /**
     * The search-result form of the same profile. Search itself arrives in Phase 3; the
     * mapping is written here, with the profile it summarises, so that the two views of a
     * tutor are defined side by side and cannot drift apart.
     */
    @Mapping(target = "tutorId", source = "profile.tutor.id")
    @Mapping(target = "fullName", source = "profile.tutor.fullName")
    @Mapping(target = "mediums", expression = "java(labels.mediums(profile.getMediums(), language))")
    TutorCardDto toCard(TutorProfile profile,
                        @Context Language language,
                        @Context ReferenceLabels labels);

    List<TutorCardDto> toCards(List<TutorProfile> profiles,
                               @Context Language language,
                               @Context ReferenceLabels labels);

    QualificationDto toQualificationDto(Qualification qualification);

    // One item method per reference type rather than one taking their shared supertype: the
    // collection mappings above are matched on the element type, and a method declared against
    // ReferenceEntity would leave that match ambiguous.

    @Mapping(target = "name", expression = "java(subject.nameIn(language))")
    ReferenceItemResponse toItem(Subject subject, @Context Language language);

    @Mapping(target = "name", expression = "java(examLevel.nameIn(language))")
    ReferenceItemResponse toItem(ExamLevel examLevel, @Context Language language);

    @Mapping(target = "name", expression = "java(syllabus.nameIn(language))")
    ReferenceItemResponse toItem(Syllabus syllabus, @Context Language language);

    @Mapping(target = "name", expression = "java(area.nameIn(language))")
    ReferenceItemResponse toItem(Area area, @Context Language language);

    /**
     * Enum names, not sentences. The client decides how to say "you still need a photo" in
     * Sinhala; the server says which field, in a key that does not change when the wording
     * does.
     */
    default List<String> toFieldNames(List<ProfileField> fields) {
        return fields.stream().map(ProfileField::name).toList();
    }
}
