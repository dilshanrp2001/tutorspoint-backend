package com.tutorspoint.tutor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One credential as the wizard submits it (FR-T3). */
public record QualificationRequest(

        @NotBlank(message = "{validation.qualification.title.required}")
        @Size(max = 200, message = "{validation.qualification.title.size}")
        String title,

        @NotBlank(message = "{validation.qualification.institution.required}")
        @Size(max = 200, message = "{validation.qualification.institution.size}")
        String institution,

        @NotNull(message = "{validation.qualification.year.required}")
        @Min(value = TutorProfileValidation.EARLIEST_QUALIFICATION_YEAR,
                message = "{validation.qualification.year.range}")
        @Max(value = TutorProfileValidation.LATEST_QUALIFICATION_YEAR,
                message = "{validation.qualification.year.range}")
        Integer yearAwarded) {
}
