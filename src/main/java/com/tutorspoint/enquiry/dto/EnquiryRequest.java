package com.tutorspoint.enquiry.dto;

import com.tutorspoint.common.domain.ClassFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A seeker — a parent or a student — opening a thread with a tutor (FR-E1).
 *
 * <p>Reference values arrive as codes, not database ids: the code is the stable identifier,
 * it means the same thing in every environment, and a request body full of codes is readable
 * in a log. The tutor is the exception — an account has no code, and {@code /api/tutors/{id}}
 * is already how the rest of the API names one.
 *
 * <p>{@code preferredAreaCode} and {@code online} are the two halves of one answer: where.
 * Exactly one of them must be given, which is a rule about the pair rather than about either
 * field, so it is enforced by {@code Enquiry.open} rather than by an annotation here.
 */
public record EnquiryRequest(

        @Schema(description = "The tutor being asked", example = "42")
        @NotNull(message = "{validation.enquiry.tutor.required}")
        Long tutorId,

        @Schema(description = "Which child this is about. Omitted by an adult student enquiring for themselves.")
        Long childProfileId,

        @Schema(description = "Subject code from /api/reference", example = "CHEMISTRY")
        @NotBlank(message = "{validation.enquiry.subject.required}")
        @Size(max = EnquiryValidation.CODE_MAX_LENGTH, message = "{validation.enquiry.code.size}")
        String subjectCode,

        @Schema(description = "Exam level code from /api/reference", example = "GCE_AL")
        @NotBlank(message = "{validation.enquiry.exam-level.required}")
        @Size(max = EnquiryValidation.CODE_MAX_LENGTH, message = "{validation.enquiry.code.size}")
        String examLevelCode,

        @Schema(description = "How the seeker would like the classes taught")
        @NotNull(message = "{validation.enquiry.format.required}")
        ClassFormat preferredFormat,

        @Schema(description = "Area code the classes would be in. Omitted when online is true.",
                example = "NUGEGODA")
        @Size(max = EnquiryValidation.CODE_MAX_LENGTH, message = "{validation.enquiry.code.size}")
        String preferredAreaCode,

        @Schema(description = "True when the seeker wants online classes. Then no area is given.")
        boolean online,

        @Schema(description = "The first message of the thread. Phone numbers and email addresses "
                + "are removed until the tutor has replied.")
        @NotBlank(message = "{validation.enquiry.message.required}")
        @Size(max = EnquiryValidation.MESSAGE_MAX_LENGTH, message = "{validation.enquiry.message.size}")
        String message) {
}
