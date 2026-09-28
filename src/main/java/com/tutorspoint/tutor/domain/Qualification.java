package com.tutorspoint.tutor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * One credential a tutor claims — a degree, a diploma, a teaching certificate (FR-T3).
 *
 * <p>Embeddable rather than an entity: a qualification has no identity or lifecycle of its
 * own, it is never queried for, and it dies with the profile that lists it. The rows live in
 * a collection table ordered by the position the tutor put them in, because that order is
 * how they read on the profile page.
 *
 * <p>A claim, not a fact. What turns it into a fact is the document review in
 * {@code verification} — the verified badge comes from there, never from this text.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Qualification {

    /** The earliest year worth accepting. A typo of 19 or 200 should not persist. */
    private static final int EARLIEST_YEAR = 1900;

    private static final int LATEST_YEAR = 2100;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "institution", nullable = false, length = 200)
    private String institution;

    @Column(name = "year_awarded", nullable = false)
    private int yearAwarded;

    public Qualification(String title, String institution, int yearAwarded) {
        this.title = requireText(title, "title").trim();
        this.institution = requireText(institution, "institution").trim();
        this.yearAwarded = requireYear(yearAwarded);
    }

    /**
     * Value semantics: two identical claims are the same claim. This is what stops a
     * double-submitted wizard step from listing the same degree twice.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Qualification that)) {
            return false;
        }
        return yearAwarded == that.yearAwarded
                && Objects.equals(title, that.title)
                && Objects.equals(institution, that.institution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, institution, yearAwarded);
    }

    @Override
    public String toString() {
        return "%s, %s (%d)".formatted(title, institution, yearAwarded);
    }

    private static int requireYear(int year) {
        if (year < EARLIEST_YEAR || year > LATEST_YEAR) {
            throw new IllegalArgumentException(
                    "yearAwarded must be between %d and %d, was %d".formatted(EARLIEST_YEAR, LATEST_YEAR, year));
        }
        return year;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
