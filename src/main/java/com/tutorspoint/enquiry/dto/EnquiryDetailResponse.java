package com.tutorspoint.enquiry.dto;

import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A whole thread, as one of its two participants sees it. Nobody else ever receives this
 * shape — a non-participant is answered as though the enquiry did not exist.
 *
 * <p>{@code contact} is the masking rule made visible in the API (FR-E2, NFR-5). It is null
 * until the tutor has replied, and from then on it holds the <em>other</em> participant's
 * details: the parent learns how to reach the tutor who answered them, and the tutor — who
 * opened the channel by answering — learns how to reach that parent. It is never anybody
 * else's contact details and never a third party's.
 *
 * <p>{@code contactRevealed} says the same thing as a non-null {@code contact}, and it is
 * here so the UI can explain the rule while it still applies: "details appear once the tutor
 * replies" is a better screen than a field that is quietly missing.
 */
public record EnquiryDetailResponse(

        Long id,

        Long tutorId,
        String tutorName,

        Long parentId,
        String parentName,

        @Schema(description = "The child this is about, when the parent named one")
        EnquiryChildDto child,

        ReferenceItemResponse subject,
        ReferenceItemResponse examLevel,
        ReferenceItemResponse preferredFormat,

        @Schema(description = "Where the parent wants the classes. Null when online is true.")
        ReferenceItemResponse preferredArea,

        boolean online,

        EnquiryStatus status,

        Instant createdAt,

        Instant firstResponseAt,

        @Schema(description = "Whether contact details are visible on this thread yet")
        boolean contactRevealed,

        @Schema(description = "The other participant's contact details. Null until the tutor replies.")
        ContactDetailsDto contact,

        @Schema(description = "The whole thread, oldest first. The first entry is the enquiry itself.")
        List<EnquiryMessageDto> messages) {
}
