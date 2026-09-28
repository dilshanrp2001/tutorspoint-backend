package com.tutorspoint.admin.dto;

import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.DocumentType;

import java.time.Instant;

/**
 * A submitted document as a reviewer sees it: the tutor's view plus who ruled on it.
 *
 * <p>Still no storage key and no URL. The reviewer opens the file through
 * {@code GET /api/documents/{id}}, the one authorised route to it, which is also what records
 * that they did.
 */
public record AdminDocumentResponse(
        Long id,
        DocumentType documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String reviewNotes,
        String reviewedByName,
        Instant reviewedAt,
        Instant uploadedAt) {
}
