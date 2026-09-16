package com.tutorspoint.admin.dto;

import java.time.LocalDate;

/**
 * The pilot's numbers (OBJ-6), for a window of calendar days in Sri Lanka time.
 *
 * <p>Two kinds of figure, and the difference matters when reading them. Registrations, searches
 * and enquiries are <em>events in the window</em>. Published and verified profiles and the review
 * queue are <em>the state now</em>, whatever window was asked for - a profile count "as of last
 * Tuesday" would need history the platform does not keep.
 *
 * @param from                    first day counted, inclusive; null when counting from the start
 * @param to                      last day counted, inclusive
 * @param registrations           accounts created in the window, by role
 * @param searches                tutor searches run in the window; paging through the results
 *                                of one is not another
 * @param enquiriesSent           enquiries sent in the window, not counting ones moderated as spam
 * @param enquiriesResponded      of those, the ones the tutor has answered
 * @param responseRate            responded / sent, from 0 to 1; null when nothing was sent
 * @param publishedProfiles       profiles live now
 * @param verifiedProfiles        of those, the ones with the verified badge
 * @param documentsAwaitingReview documents in the review queue now
 */
public record AdminMetricsResponse(
        LocalDate from,
        LocalDate to,
        Registrations registrations,
        long searches,
        long enquiriesSent,
        long enquiriesResponded,
        Double responseRate,
        long publishedProfiles,
        long verifiedProfiles,
        long documentsAwaitingReview) {

    /** Admins are provisioned, not registered, so they are not counted here. */
    public record Registrations(long tutors, long parents, long total) {
    }
}
