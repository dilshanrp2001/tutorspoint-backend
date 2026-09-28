package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One message in a thread, as stored — which is to say already scrubbed of any contact
 * details that were written while the thread was still pre-reveal. What the reader sees here
 * is what is in the database; there is no fuller version held back.
 */
public record EnquiryMessageDto(

        Long id,

        @Schema(description = "Which participant wrote it. The client compares this with its own "
                + "user id to decide which side of the thread to draw it on.")
        Long senderId,

        String senderName,

        String body,

        Instant sentAt,

        @Schema(description = "When the other participant read it. Null means unread.")
        Instant readAt) {
}
