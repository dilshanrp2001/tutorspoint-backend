package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminDocumentResponse;
import com.tutorspoint.admin.dto.AdminTutorDetail;
import com.tutorspoint.admin.dto.DocumentApprovalRequest;
import com.tutorspoint.admin.dto.DocumentRejectionRequest;
import com.tutorspoint.admin.dto.ReinstatementRequest;
import com.tutorspoint.admin.dto.SuspensionRequest;
import com.tutorspoint.admin.dto.VerificationRequest;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tutor review: documents, the verified badge, and profile moderation. {@code ROLE_ADMIN} only.
 *
 * <p>There is no document download here. A reviewer opens a file through
 * {@code GET /api/documents/{id}}, which already serves administrators and records each read -
 * a second route to the same bytes would be a second thing to secure.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: tutors", description = "Document review, verification and profile moderation")
public class AdminTutorController {

    private final AdminTutorService adminTutorService;

    @GetMapping("/tutors/{tutorId}")
    @Operation(summary = "A tutor for review",
            description = "The account, the profile (absent if never started) and every submitted document.")
    public ApiResponse<AdminTutorDetail> tutor(@PathVariable Long tutorId) {
        return ApiResponse.ok(adminTutorService.tutor(tutorId));
    }

    @PostMapping("/documents/{documentId}/approve")
    @Operation(summary = "Approve a document", description = "Only a pending document. Audited.")
    public ApiResponse<AdminDocumentResponse> approveDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody(required = false) DocumentApprovalRequest request) {
        return ApiResponse.ok(adminTutorService.approveDocument(documentId, request == null ? null : request.notes()));
    }

    @PostMapping("/documents/{documentId}/reject")
    @Operation(summary = "Reject a document",
            description = "Only a pending document. The notes are required and are shown to the tutor. Audited.")
    public ApiResponse<AdminDocumentResponse> rejectDocument(@PathVariable Long documentId,
                                                             @Valid @RequestBody DocumentRejectionRequest request) {
        return ApiResponse.ok(adminTutorService.rejectDocument(documentId, request.notes()));
    }

    @PutMapping("/tutors/{tutorId}/verification")
    @Operation(summary = "Grant or withdraw the verified badge",
            description = "Idempotent. A real change is audited; asking for the current state records nothing.")
    public ApiResponse<AdminTutorDetail> setVerified(@PathVariable Long tutorId,
                                                     @Valid @RequestBody VerificationRequest request) {
        return ApiResponse.ok(adminTutorService.setVerified(tutorId, request.verified()));
    }

    @PostMapping("/tutors/{tutorId}/profile/suspend")
    @Operation(summary = "Suspend a profile",
            description = "Removes it from search and the public page and stops the tutor editing it. "
                    + "The account is untouched. Audited with the reason given.")
    public ApiResponse<AdminTutorDetail> suspendProfile(@PathVariable Long tutorId,
                                                        @Valid @RequestBody SuspensionRequest request) {
        return ApiResponse.ok(adminTutorService.suspendProfile(tutorId, request.reason()));
    }

    @PostMapping("/tutors/{tutorId}/profile/reinstate")
    @Operation(summary = "Reinstate a profile",
            description = "Returns it to the tutor as a draft, which they publish again themselves. Audited.")
    public ApiResponse<AdminTutorDetail> reinstateProfile(
            @PathVariable Long tutorId,
            @Valid @RequestBody(required = false) ReinstatementRequest request) {
        return ApiResponse.ok(adminTutorService.reinstateProfile(tutorId, request == null ? null : request.reason()));
    }
}
