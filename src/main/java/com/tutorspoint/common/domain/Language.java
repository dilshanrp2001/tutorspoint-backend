package com.tutorspoint.common.domain;

/**
 * The languages TutorsPoint speaks. Every user-facing string — API messages,
 * notification templates, reference data — must exist in all three (NFR: trilingual).
 * Stored as the enum name, so the column reads 'EN' / 'SI' / 'TA'.
 */
public enum Language {

    /** English. The fallback when a translation is missing. */
    EN,

    /** Sinhala. */
    SI,

    /** Tamil. */
    TA
}
