package com.tutorspoint.verification.dto;

import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.DocumentType;

import java.time.Instant;

/**
 * A submitted document as its owner sees it (FR-T7).
 *
 * <p><strong>There is no storage key here and no URL.</strong> The file is fetched by document
 * id through an endpoint that authorises the caller; handing out a key would create a second
 * way to reach a NIC scan, and the whole point is that there is exactly one.
 *
 * <p>{@code reviewedBy} is absent for the same class of reason: which member of staff refused
 * a document is an audit fact, not something the tutor is owed or that would help them.
 * {@code reviewNotes} - the reason - is what they need, and that is here.
 */
public record VerificationDocumentResponse(
        Long id,
        DocumentType documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String reviewNotes,
        Instant reviewedAt,
        Instant uploadedAt) {
}
