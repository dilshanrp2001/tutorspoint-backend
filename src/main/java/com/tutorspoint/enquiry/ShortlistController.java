package com.tutorspoint.enquiry;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.enquiry.dto.ShortlistRequest;
import com.tutorspoint.enquiry.dto.ShortlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * A seeker's saved tutors (FR-P1).
 *
 * <p>Addressed by tutor id rather than by an id of its own: a parent thinks "save this tutor",
 * not "create a shortlist entry", and a client that already has the tutor on screen has
 * everything it needs to save or unsave them without first looking up a row.
 */
@RestController
@RequestMapping("/api/shortlist")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shortlist", description = "Tutors a parent or student has saved to come back to")
public class ShortlistController {

    private final ShortlistService shortlistService;

    @GetMapping
    @Operation(summary = "My shortlist",
            description = "Saved tutors, most recent first, each with the same public card the "
                    + "search results show. A tutor who has since unpublished comes back with a "
                    + "null card rather than vanishing from the list.")
    public ApiResponse<List<ShortlistResponse>> myShortlist(Locale locale) {
        return ApiResponse.ok(shortlistService.myShortlist(Language.fromLocale(locale)));
    }

    @PostMapping("/{tutorId}")
    @Operation(summary = "Save a tutor",
            description = "Saves the tutor, or rewrites the note if they are already saved. "
                    + "Idempotent: the pair is unique, so saving twice is not two entries.")
    public ApiResponse<ShortlistResponse> save(@PathVariable Long tutorId,
                                               @Valid @RequestBody(required = false) ShortlistRequest request,
                                               Locale locale) {
        // The note is optional, and so is the body that carries it. Defaulted here rather than
        // made mandatory so that saving a tutor with no note is a POST with nothing in it,
        // which is what a "save" button sends.
        ShortlistRequest safeRequest = request == null ? new ShortlistRequest(null) : request;
        return ApiResponse.ok(shortlistService.save(tutorId, safeRequest, Language.fromLocale(locale)));
    }

    @DeleteMapping("/{tutorId}")
    @Operation(summary = "Remove a tutor from my shortlist",
            description = "Removing a tutor who was not saved succeeds: the caller wanted them gone.")
    public ApiResponse<Void> remove(@PathVariable Long tutorId) {
        shortlistService.remove(tutorId);
        return ApiResponse.ok();
    }
}
