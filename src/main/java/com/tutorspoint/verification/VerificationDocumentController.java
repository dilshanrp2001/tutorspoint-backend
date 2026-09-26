package com.tutorspoint.verification;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.storage.MultipartUploads;
import com.tutorspoint.verification.domain.DocumentType;
import com.tutorspoint.verification.dto.VerificationDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Qualification documents (FR-T7).
 *
 * <p>Submission and the tutor's own list sit under {@code /api/tutors/me/documents}, where no
 * path names an owner. The download sits at {@code /api/documents/{id}} instead, because it has
 * a second legitimate caller - an administrator reviewing somebody else's file - and pretending
 * otherwise would mean either a second URL for the same bytes or a {@code /me} route that is
 * not about {@code me}.
 *
 * <p><strong>Nothing here is public.</strong> There is no route that serves a document without
 * a token, and the download authorises inside the service before a byte is read. That is the
 * difference between this controller and {@code MediaController}, and it is why photographs and
 * NIC scans do not share an endpoint.
 *
 * <p>This class is also the boundary where a servlet type stops: {@link MultipartFile} is read
 * through {@link MultipartUploads} here, and nothing below the controller knows the file
 * arrived over HTTP.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Verification documents", description = "Qualification documents submitted for manual review")
public class VerificationDocumentController {

    private final VerificationDocumentService verificationDocumentService;

    @PostMapping(path = "/api/tutors/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a document",
            description = "PDF, JPEG or PNG, up to 5 MB. The format is decided by the file's "
                    + "content, so renaming a file does not change what it is.")
    public ApiResponse<VerificationDocumentResponse> upload(@RequestParam("documentType") DocumentType documentType,
                                                            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(verificationDocumentService.upload(documentType, MultipartUploads.read(file)));
    }

    @GetMapping("/api/tutors/me/documents")
    @Operation(summary = "My documents", description = "Newest first, each with its review status.")
    public ApiResponse<List<VerificationDocumentResponse>> myDocuments() {
        return ApiResponse.ok(verificationDocumentService.myDocuments());
    }

    @DeleteMapping("/api/tutors/me/documents/{documentId}")
    @Operation(summary = "Withdraw a document",
            description = "Only while it is still pending: once a reviewer has ruled, the "
                    + "document is part of an audit record.")
    public ApiResponse<Void> deleteMyDocument(@PathVariable Long documentId) {
        verificationDocumentService.deleteMyDocument(documentId);
        return ApiResponse.ok();
    }

    @GetMapping("/api/documents/{documentId}")
    @Operation(summary = "Download a document",
            description = "The owning tutor or an administrator. Anybody else is answered as if "
                    + "the document did not exist.")
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        DocumentDownload download = verificationDocumentService.download(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.content().contentType()))
                .contentLength(download.content().sizeBytes())
                // attachment, not inline: a document is never rendered in the page, so a PDF
                // cannot run anything in our origin.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                // Private, and never in a shared cache: this is somebody's identity document.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(download.content().bytes()));
    }
}
