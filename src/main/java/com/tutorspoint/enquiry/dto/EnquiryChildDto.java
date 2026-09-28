package com.tutorspoint.enquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The child an enquiry is about, as much of them as the tutor needs to answer it.
 *
 * <p>Deliberately not the whole child profile: the school and the parent's private notes are
 * for the parent, and a tutor deciding whether they can help needs the grade and the level,
 * not the child's address book entry.
 */
@Schema(description = "The child this enquiry is about, when the parent named one")
public record EnquiryChildDto(
        Long id,
        String name,
        String grade,
        String examLevel) {
}
