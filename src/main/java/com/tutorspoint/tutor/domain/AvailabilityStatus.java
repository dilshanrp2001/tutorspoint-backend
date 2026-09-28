package com.tutorspoint.tutor.domain;

/**
 * How much room a tutor has left (FR-T6).
 *
 * <p>Deliberately not a boolean. A parent deciding whether to enquire is served by the
 * middle value — "taking a few more students" is a different answer from both "yes" and
 * "no", and it is the one an established tutor most often wants to give.
 */
public enum AvailabilityStatus {

    /** Taking new students. */
    ACCEPTING,

    /** A small number of places left. */
    LIMITED,

    /** No places. The profile stays visible; the tutor is simply not taking enquiries now. */
    FULL
}
