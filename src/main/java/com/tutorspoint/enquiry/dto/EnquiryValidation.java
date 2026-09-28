package com.tutorspoint.enquiry.dto;

import com.tutorspoint.enquiry.domain.EnquiryMessage;
import com.tutorspoint.enquiry.domain.Shortlist;

/**
 * The input shapes the enquiry endpoints accept, in one place because several DTOs enforce
 * the same rule and a Bean Validation annotation needs a compile-time constant.
 *
 * <p>Format rules only. Whether the tutor exists, whether the parent has already opened a
 * thread with them, whether they have sent too many this hour — none of that is knowable
 * from a request body, and it belongs in the service.
 *
 * <p>The length limits are read off the entities rather than repeated, so a request can
 * never be accepted at the edge and then rejected by the column it is going into.
 */
public final class EnquiryValidation {

    /** Reference values travel as codes, which {@code ReferenceEntity} caps at 60. */
    public static final int CODE_MAX_LENGTH = 60;

    public static final int MESSAGE_MAX_LENGTH = EnquiryMessage.MAX_BODY_LENGTH;

    public static final int NOTE_MAX_LENGTH = Shortlist.MAX_NOTE_LENGTH;

    private EnquiryValidation() {
    }
}
