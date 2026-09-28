package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A further message in an existing thread, from either participant. */
public record EnquiryMessageRequest(

        @Schema(description = "The message. Phone numbers and email addresses are removed while "
                + "the thread is still awaiting the tutor's first reply.")
        @NotBlank(message = "{validation.enquiry.message.required}")
        @Size(max = EnquiryValidation.MESSAGE_MAX_LENGTH, message = "{validation.enquiry.message.size}")
        String body) {
}
