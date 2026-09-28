package com.tutorspoint.enquiry.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The enquiry policy: how much one parent may send, and where the links in the resulting
 * notifications point.
 *
 * <p>The caps are policy rather than secrets, so they carry defaults in
 * {@code application.yml}. The link target is a frontend URL and therefore differs per
 * environment, which is why it comes from {@code FRONTEND_BASE_URL}.
 *
 * <p>This is a domain cap, counted against what is in the database, not the HTTP rate limiter
 * — that arrives in Phase 5.2 and answers a different question. The two are complementary: a
 * per-IP limiter stops a script, and this stops a signed-in account from working through every
 * tutor in a district one enquiry at a time.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.enquiry")
public record EnquiryProperties(

        /**
         * Enquiries one parent may open per hour. Generous for a parent comparing tutors in
         * an evening, and low enough that nobody mails an entire district.
         */
        @Min(1) int maxPerHour,

        /** Frontend page that opens one thread; the enquiry id is appended to it. */
        @NotBlank String threadUrl) {

    /** The deep link a notification sends its recipient to. */
    public String threadUrlFor(Long enquiryId) {
        return threadUrl.endsWith("/") ? threadUrl + enquiryId : threadUrl + "/" + enquiryId;
    }
}
