package com.tutorspoint.verification;

import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.InvalidUploadException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.storage.UploadedFile;
import com.tutorspoint.verification.domain.DocumentType;
import com.tutorspoint.verification.dto.VerificationDocumentResponse;

import java.util.List;

/**
 * Qualification documents a tutor submits for manual verification (FR-T7).
 *
 * <p>The first three methods take no tutor id: the owner is the caller, as everywhere else.
 * {@link #download} is the exception and is the reason this interface exists in this shape -
 * an administrator must be able to read any document, so exactly one method takes an id, and
 * it authorises rather than scopes.
 */
public interface VerificationDocumentService {

    /**
     * Stores a document and records it as pending review.
     *
     * @throws InvalidUploadException         if the bytes are not PDF, JPEG or PNG, or the file
     *                                        is over the limit - checked against the content,
     *                                        never the filename or the declared type
     * @throws BusinessRuleViolationException if the tutor already has as many documents as the
     *                                        platform will hold for one account
     */
    VerificationDocumentResponse upload(DocumentType documentType, UploadedFile file);

    /** The caller's own documents, newest first. */
    List<VerificationDocumentResponse> myDocuments();

    /**
     * Withdraws one of the caller's own documents, file included.
     *
     * @throws ResourceNotFoundException      if the id is unknown or belongs to another tutor -
     *                                        the same answer either way
     * @throws BusinessRuleViolationException if a reviewer has already ruled on it
     */
    void deleteMyDocument(Long documentId);

    /**
     * The file itself, for the owning tutor or an administrator.
     *
     * <p>Never a public URL and never a redirect to one: the authorisation happens here, on
     * every single read, and the bytes are returned to a controller that streams them.
     *
     * @throws ResourceNotFoundException if the document does not exist, or the caller is
     *                                   neither its owner nor an administrator
     */
    DocumentDownload download(Long documentId);
}
