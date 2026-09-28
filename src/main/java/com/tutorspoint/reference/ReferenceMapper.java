package com.tutorspoint.reference;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.ReferenceEntity;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.reference.dto.TownResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * The one place a reference entity becomes a response DTO.
 *
 * <p>The caller's language travels as a {@code @Context} parameter rather than as a source
 * of its own, so a single {@link #toItem} covers all four types: the name is whatever
 * {@link ReferenceEntity#nameIn} decides, including its English fallback, and no caller
 * re-implements that choice.
 *
 * <p>Entity ids are not mapped anywhere here, and that is deliberate — see
 * {@link ReferenceItemResponse}.
 */
@Mapper
public interface ReferenceMapper {

    @Mapping(target = "name", expression = "java(entity.nameIn(language))")
    ReferenceItemResponse toItem(ReferenceEntity entity, @Context Language language);

    List<ReferenceItemResponse> toSubjects(List<Subject> subjects, @Context Language language);

    List<ReferenceItemResponse> toExamLevels(List<ExamLevel> examLevels, @Context Language language);

    List<ReferenceItemResponse> toSyllabuses(List<Syllabus> syllabuses, @Context Language language);

    @Mapping(target = "name", expression = "java(district.nameIn(language))")
    DistrictResponse toDistrict(Area district, @Context Language language);

    @Mapping(target = "name", expression = "java(town.nameIn(language))")
    TownResponse toTown(Area town, @Context Language language);

    List<DistrictResponse> toDistricts(List<Area> districts, @Context Language language);
}
