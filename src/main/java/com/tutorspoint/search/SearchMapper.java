package com.tutorspoint.search;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.search.dto.FacetCount;
import com.tutorspoint.search.dto.TutorSearchHit;
import com.tutorspoint.tutor.TutorMapper;
import com.tutorspoint.tutor.domain.TutorProfile;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Search results as DTOs. The card itself is {@link TutorMapper}'s - one definition of what a
 * card shows, used by search rather than restated here - and this mapper adds what only search
 * knows: the sponsored flag, and the facet counts.
 *
 * <p>Constructor injection, so a unit test can build it around a real {@code TutorMapperImpl}.
 */
@Mapper(uses = TutorMapper.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SearchMapper {

    @Mapping(target = "tutor", source = "profile")
    TutorSearchHit toHit(TutorProfile profile,
                         boolean featured,
                         @Context Language language,
                         @Context ReferenceLabels labels);

    /** Largest first, then by code, so the order is stable between two identical searches. */
    default List<FacetCount> toFacetCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new FacetCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(FacetCount::count).reversed().thenComparing(FacetCount::code))
                .toList();
    }
}
