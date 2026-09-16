package com.tutorspoint.search.event;

import java.time.Instant;

/**
 * Somebody ran a tutor search.
 *
 * <p>Carries the moment and nothing about the query. The only consumer counts searches, and an
 * event that carried the criteria would be an invitation to start keeping them.
 *
 * @param at when the search ran
 */
public record TutorSearchPerformedEvent(Instant at) {
}
