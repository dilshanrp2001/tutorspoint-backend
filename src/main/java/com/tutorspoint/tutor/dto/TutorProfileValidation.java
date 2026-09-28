package com.tutorspoint.tutor.dto;

/**
 * The input shapes the tutor profile endpoints accept, in one place because an annotation
 * needs a compile-time constant and several of these bounds are repeated.
 *
 * <p>Format and range only. Whether a subject code exists, whether the profile is complete
 * enough to publish - neither is knowable from a request body, and both live where they can
 * be answered: the service and the entity.
 */
public final class TutorProfileValidation {

    public static final int HEADLINE_MAX = 200;

    public static final int BIO_MAX = 5000;

    public static final int URL_MAX = 500;

    /** A profile listing more than this is padding, not teaching. */
    public static final int MAX_SUBJECTS = 30;

    public static final int MAX_QUALIFICATIONS = 20;

    /** Longer than any teaching career, and short enough to catch a year typed into the box. */
    public static final int MAX_YEARS_OF_EXPERIENCE = 70;

    /** Kilometres. Sri Lanka is about 450 km end to end; beyond this, teach online. */
    public static final int MAX_TRAVEL_RADIUS_KM = 200;

    public static final int EARLIEST_QUALIFICATION_YEAR = 1900;

    public static final int LATEST_QUALIFICATION_YEAR = 2100;

    /** Rupees, so eight integer digits is tens of millions - a ceiling on typos, not on ambition. */
    public static final int FEE_INTEGER_DIGITS = 8;

    public static final int FEE_FRACTION_DIGITS = 2;

    private TutorProfileValidation() {
    }
}
