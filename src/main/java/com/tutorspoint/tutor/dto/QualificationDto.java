package com.tutorspoint.tutor.dto;

/**
 * One credential as the profile page shows it.
 *
 * <p>A claim until the document behind it is reviewed. Nothing here says verified, and that
 * is deliberate: the badge is a property of the tutor, awarded in {@code verification}, not a
 * flag a tutor can attach to a line of their own text.
 */
public record QualificationDto(
        String title,
        String institution,
        int yearAwarded) {
}
