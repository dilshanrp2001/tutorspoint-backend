package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How much is waiting for the caller, across all of their threads.
 *
 * <p>An object rather than a bare number so the badge can grow a second figure — unread
 * threads, say — without changing what the endpoint returns.
 */
public record EnquiryUnreadCountResponse(

        @Schema(description = "Messages the caller has not read, in every thread they are part of")
        long unreadCount) {
}
