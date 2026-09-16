package com.tutorspoint.enquiry.dto;

import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One row of an inbox, for either role.
 *
 * <p>The same shape both ways round: a parent's sent list and a tutor's received list are the
 * same threads read from opposite ends, and giving each its own DTO would mean writing the
 * unread rule, the preview rule and the status rule twice.
 *
 * <p>Carries no contact details at all, revealed or not. A list is browsed, and a phone
 * number that appears in a list is a phone number that is scraped; the reveal happens in the
 * thread the tutor actually answered, and nowhere else.
 */
public record EnquirySummaryResponse(

        Long id,

        Long tutorId,
        String tutorName,

        Long parentId,
        String parentName,

        ReferenceItemResponse subject,
        ReferenceItemResponse examLevel,

        @Schema(description = "How the parent asked for the classes to be taught")
        ReferenceItemResponse preferredFormat,

        @Schema(description = "Where the parent wants the classes. Null when online is true.")
        ReferenceItemResponse preferredArea,

        boolean online,

        EnquiryStatus status,

        Instant createdAt,

        @Schema(description = "When the tutor first replied. Also the moment contact details "
                + "became visible on this thread.")
        Instant firstResponseAt,

        @Schema(description = "First line of the most recent message, for the list row")
        String lastMessagePreview,

        Instant lastMessageAt,

        @Schema(description = "Messages the caller has not read yet. What the badge shows.")
        long unreadCount) {
}
