package com.tutorspoint.verification.repository;

import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.VerificationDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Submitted documents.
 *
 * <p>{@link #findByIdAndTutorId} is the owner-scoped finder every tutor-facing use case goes
 * through: the ownership rule is in the query, so another tutor's document id is not forbidden,
 * it is absent - and the endpoint cannot be used to discover that a document exists.
 *
 * <p>{@link #findById}, inherited, is used only where an administrator is entitled to any
 * document, and the caller there authorises before it reads.
 */
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, Long> {

    /** Newest first: the tutor's own list is a history, and the last upload is what they just did. */
    List<VerificationDocument> findByTutorIdOrderByCreatedAtDesc(Long tutorId);

    Optional<VerificationDocument> findByIdAndTutorId(Long id, Long tutorId);

    long countByTutorId(Long tutorId);

    /** As {@link #findByTutorIdOrderByCreatedAtDesc}, with the reviewer loaded for the admin view. */
    @EntityGraph(attributePaths = "reviewedBy")
    List<VerificationDocument> findWithReviewerByTutorIdOrderByCreatedAtDesc(Long tutorId);

    /** The review queue's size, for the metrics summary. */
    long countByStatus(DocumentStatus status);
}
