package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Saving a tutor, with the parent's own reminder of why (FR-P1).
 *
 * <p>The note is optional and private: it is what the parent wants to remember, not feedback,
 * and it is never shown to the tutor.
 */
public record ShortlistRequest(

        @Schema(description = "A private note. Only the account that wrote it ever sees it.",
                example = "Cheaper than the others, but a 40 minute drive")
        @Size(max = EnquiryValidation.NOTE_MAX_LENGTH, message = "{validation.shortlist.note.size}")
        String note) {
}
