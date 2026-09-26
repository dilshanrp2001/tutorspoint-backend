package com.tutorspoint.common.domain;

import java.util.Locale;

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
    TA;

    /**
     * The language a caller asked for, from the locale Spring resolved for the request.
     *
     * <p>Never throws and never returns null: an {@code Accept-Language} header is client
     * input, and a header naming a language we do not speak — or naming nothing at all —
     * is not an error, it is a request for the fallback.
     */
    public static Language fromLocale(Locale locale) {
        if (locale == null) {
            return EN;
        }
        return switch (locale.getLanguage()) {
            case "si" -> SI;
            case "ta" -> TA;
            default -> EN;
        };
    }

    /** The locale to resolve {@code messages*.properties} against for this language. */
    public Locale toLocale() {
        return Locale.of(name().toLowerCase(Locale.ROOT));
    }
}
