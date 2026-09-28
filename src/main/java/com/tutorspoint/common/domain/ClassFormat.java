package com.tutorspoint.common.domain;

/**
 * How a class is delivered. A decision-critical filter: a parent looking for individual
 * attention and a parent looking for an affordable mass class are not looking for the
 * same tutor.
 *
 * <p>A closed set, like {@link Medium}, so it is an enum rather than a reference table;
 * the trilingual display names are message keys ({@code reference.class-format.*}).
 */
public enum ClassFormat {

    /** One tutor, one student. */
    ONE_TO_ONE,

    /** A handful of students, typically at the tutor's or the student's home. */
    SMALL_GROUP,

    /** The institute-scale class familiar from Sri Lankan tuition. */
    MASS_CLASS,

    /** Delivered remotely, wherever either party is. */
    ONLINE
}
