package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How to reach the other participant, once the thread has earned it.
 *
 * <p>A type of its own rather than two more fields on the enquiry response, and that is the
 * point: contact details are either present as a whole or absent as a whole. There is no
 * state in which an email leaks while a phone number is masked, and a null here is the API
 * saying "not yet" in one unmistakable place — see {@code Enquiry.contactRevealed()} for
 * when that changes.
 */
@Schema(description = "Present only after the tutor has replied (FR-E2). Null until then.")
public record ContactDetailsDto(
        String fullName,
        String email,
        String phoneNumber) {
}
