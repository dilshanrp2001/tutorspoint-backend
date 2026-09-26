package com.tutorspoint.admin.dto;

import com.tutorspoint.verification.domain.VerificationDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refusing a document. The notes are required and are shown to the tutor: a rejection with no
 * reason produces a re-upload of the same file.
 */
public record DocumentRejectionRequest(
        @NotBlank(message = "{validation.admin.rejection-notes.required}")
        @Size(max = VerificationDocument.MAX_REVIEW_NOTES, message = "{validation.admin.notes.size}")
        String notes) {
}
