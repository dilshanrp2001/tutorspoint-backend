package com.tutorspoint.tutor.dto;

import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;
import com.tutorspoint.tutor.domain.ProfileStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A whole tutor profile: what the owner sees in the wizard, and what a parent sees on the
 * public profile page.
 *
 * <p><strong>There is no email address and no phone number on this record, and there must
 * never be one.</strong> The public endpoint returns this type, so a contact field added here
 * would be published to the internet the day it was added - and the platform earns its keep
 * by carrying the first contact itself (FR-E). Making the field absent rather than filtered is
 * what stops that from being one forgotten condition away.
 *
 * <p>Reference values arrive as {@code {code, name}} already rendered in the caller's
 * language, the same shape {@code /api/reference} serves, so a client can show a profile
 * without holding a lookup table.
 *
 * <p>{@code status}, {@code complete} and {@code missingFields} are wizard fields. They are
 * harmless on a published profile - it is by definition complete and live - and they save the
 * owner view from needing a DTO of its own.
 */
public record TutorProfileDto(

        /* The tutor account id. It is what /api/tutors/{id} is addressed by. */
        Long tutorId,

        String fullName,

        String headline,

        String bio,

        String photoUrl,

        String introVideoUrl,

        List<ReferenceItemResponse> subjects,

        List<ReferenceItemResponse> examLevels,

        List<ReferenceItemResponse> syllabuses,

        List<ReferenceItemResponse> mediums,

        List<ReferenceItemResponse> classFormats,

        List<QualificationDto> qualifications,

        Integer yearsOfExperience,

        BigDecimal feeMin,

        BigDecimal feeMax,

        FeeUnit feeUnit,

        ReferenceItemResponse homeBaseArea,

        List<ReferenceItemResponse> areasServed,

        Integer travelRadiusKm,

        boolean availableOnline,

        AvailabilityStatus availabilityStatus,

        ProfileStatus status,

        boolean verified,

        Instant verifiedAt,

        boolean complete,

        /* Stable enum names, so the client renders the reason in si / ta / en. */
        List<String> missingFields) {
}
