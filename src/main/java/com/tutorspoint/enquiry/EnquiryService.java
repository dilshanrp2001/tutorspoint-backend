package com.tutorspoint.enquiry;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.dto.EnquiryDetailResponse;
import com.tutorspoint.enquiry.dto.EnquiryListResponse;
import com.tutorspoint.enquiry.dto.EnquiryMessageRequest;
import com.tutorspoint.enquiry.dto.EnquiryRequest;

/**
 * On-platform enquiries between a parent and a tutor (FR-E1 - FR-E3).
 *
 * <p><strong>No signature carries a participant.</strong> The caller is always one of the two,
 * taken from the security context, and every thread is reached through a query that already
 * filters by them. A non-participant therefore does not get a "forbidden" — they get
 * {@link ResourceNotFoundException}, the same answer an id that does not exist gives, so the
 * endpoints cannot be used to discover which enquiries are out there or who is talking to whom.
 *
 * <p>Nothing here mentions notifications. Creating an enquiry and replying to one publish
 * domain events; what follows from those is the listeners' business, and this service would
 * work unchanged if there were none (Observer, architecture section 7).
 */
public interface EnquiryService {

    /**
     * Opens a thread with a tutor and publishes {@code EnquiryCreatedEvent}.
     *
     * <p>The message is scrubbed of phone numbers and email addresses before it is stored:
     * the masking rule would be decorative if the first message could carry a number around it.
     *
     * @return the new thread, with contact details masked — the tutor has not replied yet, so
     *         by definition nothing is revealed
     * @throws ResourceNotFoundException        if the tutor has no published profile, or a
     *                                          reference code is not recognised
     * @throws BusinessRuleViolationException   if the caller's account is not active, they
     *                                          already have a live thread with this tutor, or
     *                                          they have passed the hourly cap
     */
    EnquiryDetailResponse create(EnquiryRequest request, Language language);

    /**
     * The caller's threads, newest first — sent if they are a parent, received if they are a
     * tutor (FR-P2). Same shape either way: it is one list of threads read from two ends.
     *
     * @param status optional filter; null returns every status
     */
    EnquiryListResponse myEnquiries(EnquiryStatus status, int page, int size, Language language);

    /**
     * One whole thread, as the caller sees it.
     *
     * <p>Reading has three side effects, all of them the point of reading: a tutor opening an
     * unopened enquiry moves it to VIEWED, anything the caller had not seen is marked read, and
     * — once the tutor has replied — the other participant's contact details are attached and
     * the reveal is logged.
     *
     * @throws ResourceNotFoundException if the id is unknown or the caller is not a participant
     */
    EnquiryDetailResponse thread(Long enquiryId, Language language);

    /**
     * Adds a message to a thread the caller is in. A tutor's first message publishes
     * {@code EnquiryRespondedEvent} and opens the contact channel.
     *
     * <p>Bodies are scrubbed until that moment, not only on the first message: a channel that
     * is closed on message one and open on message two is not closed.
     *
     * @throws ResourceNotFoundException      as {@link #thread} does
     * @throws BusinessRuleViolationException if the thread is closed or was marked spam
     */
    EnquiryDetailResponse addMessage(Long enquiryId, EnquiryMessageRequest request, Language language);

    /**
     * Ends the conversation. Either participant may, and neither needs the other's agreement.
     *
     * @throws ResourceNotFoundException as {@link #thread} does
     */
    EnquiryDetailResponse close(Long enquiryId, Language language);
}
