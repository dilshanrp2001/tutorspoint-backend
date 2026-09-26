package com.tutorspoint.auth.dto;

/** A child sub-profile as returned to its owning parent (FR-A5). */
public record ChildProfileResponse(
        Long id,
        String name,
        String grade,
        String examLevel,
        String school,
        String notes) {
}
