package com.tutorspoint.admin.dto;

import com.tutorspoint.tutor.domain.ProfileField;
import com.tutorspoint.tutor.domain.ProfileStatus;

import java.time.Instant;
import java.util.List;

/**
 * What a moderator needs to judge a profile: its state, its badge, and the free text the tutor
 * wrote, which is where anything that needs taking down would be.
 */
public record AdminTutorProfileView(
        ProfileStatus status,
        boolean verified,
        Instant verifiedAt,
        String headline,
        String bio,
        String photoUrl,
        String introVideoUrl,
        Integer yearsOfExperience,
        List<ProfileField> missingFields,
        Instant updatedAt) {
}
