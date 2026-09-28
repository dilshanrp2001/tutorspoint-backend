package com.tutorspoint.tutor.domain;

/**
 * What a fee is charged per (FR-T3).
 *
 * <p>Required alongside every fee, because a bare number is not comparable: Rs. 2,000 is
 * cheap for a month of classes and expensive for one hour. Search converts through this
 * before it compares two tutors, so the unit is part of the fee, not a display note.
 */
public enum FeeUnit {

    PER_HOUR,

    PER_MONTH,

    /** One session, whatever its length — how mass classes are usually priced. */
    PER_CLASS
}
