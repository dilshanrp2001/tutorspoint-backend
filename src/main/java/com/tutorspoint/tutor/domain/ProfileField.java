package com.tutorspoint.tutor.domain;

/**
 * A part of the profile that publishing requires. The values a profile is still missing are
 * what {@link TutorProfile#missingFields()} returns, and what the wizard's progress
 * indicator is drawn from.
 *
 * <p>An enum rather than a list of sentences: these travel to a client that renders them in
 * Sinhala, Tamil or English, so what crosses the wire has to be a stable key. Adding a
 * requirement here is a compile-time prompt to translate it, not a silent English string in
 * an API payload.
 */
public enum ProfileField {

    /** A parent will not enquire with a faceless profile; the photo is not optional. */
    PHOTO,

    BIO,

    SUBJECTS,

    EXAM_LEVELS,

    MEDIUMS,

    CLASS_FORMATS,

    /** Both ends of the range and the unit they are charged in. */
    FEE_RANGE,

    /** At least one area served, or online availability — a tutor reachable nowhere is not findable. */
    LOCATION
}
