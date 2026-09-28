package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of an inbox, with what a pager needs to draw itself.
 *
 * <p>The page metadata is spelled out rather than returned as Spring's {@code Page}: that
 * type serialises to a shape which is not part of any contract we control, and it has
 * changed between Spring versions. This is ours.
 */
public record EnquiryListResponse(

        List<EnquirySummaryResponse> enquiries,

        @Schema(description = "Total matching threads, across all pages")
        long total,

        @Schema(description = "Zero-based page index")
        int page,

        int size) {
}
