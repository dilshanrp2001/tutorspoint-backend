package com.tutorspoint.enquiry.dto;

import com.tutorspoint.tutor.dto.TutorCardDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A saved tutor (FR-P1), carrying the same card the search results use.
 *
 * <p>Reusing {@code TutorCardDto} is deliberate: a shortlist is a list of tutors the parent
 * has already compared, and it must show the same decision-critical facts, rendered the same
 * way, as the results they were compared in. A second, slightly different tutor summary would
 * drift from the first the day either changed.
 *
 * <p>{@code tutor} is null when the tutor has since taken their profile down. The row still
 * comes back — the parent saved it and their note is theirs — so the UI can say the listing is
 * no longer available rather than silently dropping an entry the parent is looking for. That
 * is why {@code tutorId} and {@code tutorName} are here in their own right and not only inside
 * the card.
 *
 * <p>Carries no contact details, and not because they are masked: saving a tutor is not
 * contacting one, and a shortlist has no reveal rule because it has nothing to reveal.
 */
public record ShortlistResponse(

        Long id,

        Long tutorId,

        String tutorName,

        @Schema(description = "The public card for this tutor. Null if they have unpublished their profile.")
        TutorCardDto tutor,

        @Schema(description = "The seeker's private note. Never shown to the tutor.")
        String note,

        @Schema(description = "When the tutor was saved")
        Instant savedAt) {
}
