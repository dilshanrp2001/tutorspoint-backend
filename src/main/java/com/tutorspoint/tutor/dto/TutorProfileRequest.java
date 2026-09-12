package com.tutorspoint.tutor.dto;

import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * A whole profile draft, as the wizard saves it (FR-T1 - FR-T6).
 *
 * <p>One request for all six steps rather than one per step. The wizard autosaves the whole
 * document it is holding, so a partial save cannot leave two steps disagreeing, and a tutor
 * who abandons the browser on step four still has steps one to three exactly as they left
 * them.
 *
 * <p><strong>Almost everything is optional</strong>, and that is the point of a draft: the
 * completeness rule belongs to {@code TutorProfile.publish()}, and enforcing a piece of it
 * here would stop a tutor saving half a profile and coming back after supper. What is
 * validated here is only what can be judged from the request alone - lengths, ranges, and
 * that a fee is a number.
 *
 * <p>Reference values travel as codes, never as ids or names: a code means the same thing in
 * every environment and reads in a log. Unknown codes are rejected by the service, which is
 * the only layer that can know which exist.
 */
public record TutorProfileRequest(

        @Size(max = TutorProfileValidation.HEADLINE_MAX, message = "{validation.profile.headline.size}")
        String headline,

        @Size(max = TutorProfileValidation.BIO_MAX, message = "{validation.profile.bio.size}")
        String bio,

        @Size(max = TutorProfileValidation.URL_MAX, message = "{validation.profile.photo-url.size}")
        String photoUrl,

        @Size(max = TutorProfileValidation.URL_MAX, message = "{validation.profile.intro-video-url.size}")
        String introVideoUrl,

        @Size(max = TutorProfileValidation.MAX_SUBJECTS, message = "{validation.profile.subjects.size}")
        Set<String> subjectCodes,

        Set<String> examLevelCodes,

        Set<String> syllabusCodes,

        Set<String> areaServedCodes,

        Set<Medium> mediums,

        Set<ClassFormat> classFormats,

        @Valid
        @Size(max = TutorProfileValidation.MAX_QUALIFICATIONS, message = "{validation.profile.qualifications.size}")
        List<QualificationRequest> qualifications,

        @PositiveOrZero(message = "{validation.profile.years-of-experience.range}")
        @Max(value = TutorProfileValidation.MAX_YEARS_OF_EXPERIENCE,
                message = "{validation.profile.years-of-experience.range}")
        Integer yearsOfExperience,

        @PositiveOrZero(message = "{validation.profile.fee.range}")
        @Digits(integer = TutorProfileValidation.FEE_INTEGER_DIGITS,
                fraction = TutorProfileValidation.FEE_FRACTION_DIGITS,
                message = "{validation.profile.fee.range}")
        BigDecimal feeMin,

        @PositiveOrZero(message = "{validation.profile.fee.range}")
        @Digits(integer = TutorProfileValidation.FEE_INTEGER_DIGITS,
                fraction = TutorProfileValidation.FEE_FRACTION_DIGITS,
                message = "{validation.profile.fee.range}")
        BigDecimal feeMax,

        FeeUnit feeUnit,

        String homeBaseAreaCode,

        @Min(value = 0, message = "{validation.profile.travel-radius.range}")
        @Max(value = TutorProfileValidation.MAX_TRAVEL_RADIUS_KM,
                message = "{validation.profile.travel-radius.range}")
        Integer travelRadiusKm,

        boolean availableOnline,

        /*
         * The one required field. Every other answer can wait for the next sitting, but a
         * profile with no capacity answer would have to default to one, and a wrong default
         * here sends a parent to a tutor who is full.
         */
        @NotNull(message = "{validation.profile.availability-status.required}")
        AvailabilityStatus availabilityStatus) {
}
