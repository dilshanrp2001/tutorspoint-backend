package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A child sub-profile a parent creates or edits (FR-A5).
 *
 * <p>grade and examLevel are free text until Phase 2 lands the translatable exam-level
 * reference data; they become references to it then, so a search filter and a child profile
 * cannot disagree about what O/L is called.
 */
public record ChildProfileRequest(

        @NotBlank(message = "{validation.child.name.required}")
        @Size(max = 150, message = "{validation.child.name.size}")
        String name,

        @NotBlank(message = "{validation.child.grade.required}")
        @Size(max = 50, message = "{validation.child.grade.required}")
        String grade,

        @NotBlank(message = "{validation.child.exam-level.required}")
        @Size(max = 50, message = "{validation.child.exam-level.required}")
        String examLevel,

        @Size(max = 200, message = "{validation.child.school.size}")
        String school,

        @Size(max = 1000, message = "{validation.child.notes.size}")
        String notes) {
}
