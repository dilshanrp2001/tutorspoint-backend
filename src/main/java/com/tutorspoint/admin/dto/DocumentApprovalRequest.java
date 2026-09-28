package com.tutorspoint.admin.dto;

import com.tutorspoint.verification.domain.VerificationDocument;
import jakarta.validation.constraints.Size;

/** Accepting a document. Notes are optional: an approval needs no explanation to act on. */
public record DocumentApprovalRequest(
        @Size(max = VerificationDocument.MAX_REVIEW_NOTES, message = "{validation.admin.notes.size}")
        String notes) {
}
