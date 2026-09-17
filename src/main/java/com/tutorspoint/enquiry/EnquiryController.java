package com.tutorspoint.enquiry;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.dto.EnquiryDetailResponse;
import com.tutorspoint.enquiry.dto.EnquiryListResponse;
import com.tutorspoint.enquiry.dto.EnquiryMessageRequest;
import com.tutorspoint.enquiry.dto.EnquiryRequest;
import com.tutorspoint.enquiry.dto.EnquiryUnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * On-platform enquiries (FR-E1 - FR-E3).
 *
 * <p>Every route here requires a token. That is what a guest meets instead of a login prompt:
 * the API answers 401 and the frontend turns it into "sign in to contact this tutor", because
 * the decision about what a guest sees is the client's and the rule about who may send is ours.
 *
 * <p>One inbox path for both roles rather than {@code /api/tutors/me/enquiries} and
 * {@code /api/parents/me/enquiries}. A parent's sent list and a tutor's received list are the
 * same threads read from opposite ends, and the caller's role already says which end they are
 * standing at — two paths would be two chances for the role rules to drift apart.
 */
@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Enquiries", description = "Contacting a tutor, and the conversation that follows")
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask a tutor a question",
            description = "Opens a thread with a tutor. Parents and students only, and only active accounts. "
                    + "Phone numbers and email addresses in the message are replaced with a notice "
                    + "until the tutor has replied. Contact details are masked on the response, "
                    + "because by definition the tutor has not answered yet.")
    public ApiResponse<EnquiryDetailResponse> create(@Valid @RequestBody EnquiryRequest request,
                                                     Locale locale) {
        return ApiResponse.ok(enquiryService.create(request, Language.fromLocale(locale)));
    }

    @GetMapping
    @Operation(summary = "My enquiries",
            description = "Role-aware: a parent or student sees what they sent (FR-P2), a tutor what they "
                    + "received. Newest first. No contact details appear in a list, revealed or not.")
    public ApiResponse<EnquiryListResponse> myEnquiries(
            @RequestParam(required = false) EnquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Locale locale) {
        return ApiResponse.ok(enquiryService.myEnquiries(status, page, size, Language.fromLocale(locale)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many messages are waiting for me",
            description = "Unread messages across all of the caller's threads, for the badge in the "
                    + "header. Uses the same rule as each inbox row's unreadCount, with no page limit. "
                    + "Zero for an administrator.")
    public ApiResponse<EnquiryUnreadCountResponse> unreadCount() {
        return ApiResponse.ok(enquiryService.unreadCount());
    }

    @GetMapping("/{enquiryId}")
    @Operation(summary = "Read a thread",
            description = "Participants only; anybody else is answered as though it did not exist. "
                    + "A tutor opening an unopened enquiry marks it VIEWED, and anything the caller "
                    + "had not read is marked read. Once the tutor has replied, the other "
                    + "participant's contact details are included and the reveal is logged.")
    public ApiResponse<EnquiryDetailResponse> thread(@PathVariable Long enquiryId, Locale locale) {
        return ApiResponse.ok(enquiryService.thread(enquiryId, Language.fromLocale(locale)));
    }

    @PostMapping("/{enquiryId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reply on a thread",
            description = "Either participant. The tutor's first message opens the contact channel "
                    + "for both sides. Bodies are scrubbed of contact details until that happens.")
    public ApiResponse<EnquiryDetailResponse> addMessage(@PathVariable Long enquiryId,
                                                         @Valid @RequestBody EnquiryMessageRequest request,
                                                         Locale locale) {
        return ApiResponse.ok(enquiryService.addMessage(enquiryId, request, Language.fromLocale(locale)));
    }

    @PostMapping("/{enquiryId}/close")
    @Operation(summary = "Close a thread",
            description = "Either participant may end the conversation. No further messages are accepted.")
    public ApiResponse<EnquiryDetailResponse> close(@PathVariable Long enquiryId, Locale locale) {
        return ApiResponse.ok(enquiryService.close(enquiryId, Language.fromLocale(locale)));
    }
}
