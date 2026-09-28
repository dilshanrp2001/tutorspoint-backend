package com.tutorspoint.tutor.dto;

import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;

import java.math.BigDecimal;
import java.util.List;

/**
 * A tutor as a search result (FR-S3): the facts a parent decides on, and nothing else.
 *
 * <p>Short on purpose. A card is scanned on a phone next to nine others, so every field here
 * has to earn its line - who they are, what they teach, at what level, in what language, for
 * how much, from where, whether they have room, and whether we checked them. The bio,
 * syllabuses, qualifications and travel radius are all real, and all reasons to open the
 * profile rather than things to read in a list.
 *
 * <p>It is also what keeps a result page cheap: a list of these is a fraction of the payload
 * a list of {@link TutorProfileDto} would be. Like that record, it carries no contact detail
 * and never will.
 */
public record TutorCardDto(

        Long tutorId,

        String fullName,

        String headline,

        String photoUrl,

        List<ReferenceItemResponse> subjects,

        List<ReferenceItemResponse> examLevels,

        List<ReferenceItemResponse> mediums,

        Integer yearsOfExperience,

        BigDecimal feeMin,

        BigDecimal feeMax,

        FeeUnit feeUnit,

        ReferenceItemResponse homeBaseArea,

        boolean availableOnline,

        AvailabilityStatus availabilityStatus,

        boolean verified,

        /* Null until the first review; the client shows "new" rather than zero stars. */
        BigDecimal averageRating,

        int reviewCount) {
}
