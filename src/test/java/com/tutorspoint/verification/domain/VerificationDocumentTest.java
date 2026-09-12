package com.tutorspoint.verification.domain;

import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The two rules a submitted document keeps for itself: a decision is final and always has an
 * author, and a tutor may withdraw a document only before somebody has ruled on it.
 */
class VerificationDocumentTest {

    private static final String STORAGE_KEY = "documents/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.pdf";
    private static final Instant REVIEWED_AT = Instant.parse("2026-09-15T09:30:00Z");

    @Test
    @DisplayName("a new submission is pending, unreviewed, and records what was actually stored")
    void startsPending() {
        VerificationDocument document = submitted();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.isPending()).isTrue();
        assertThat(document.getReviewedBy()).isNull();
        assertThat(document.getReviewedAt()).isNull();
        assertThat(document.getReviewNotes()).isNull();
        assertThat(document.getStorageKey()).isEqualTo(STORAGE_KEY);
        assertThat(document.getOriginalFilename()).isEqualTo("degree.pdf");
        // The detected type and the real size, not anything the client said.
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(document.getSizeBytes()).isEqualTo(TestPdf.BYTES.length);
    }

    @Test
    @DisplayName("the filename is a label, so a path in it is reduced to its last segment")
    void keepsOnlyTheDisplayableFilename() {
        VerificationDocument document = new VerificationDocument(tutor(), DocumentType.NIC, STORAGE_KEY,
                new UploadedFile("../../etc/passwd", TestPdf.BYTES), content());

        assertThat(document.getOriginalFilename()).isEqualTo("passwd");
    }

    @Nested
    @DisplayName("a review decision")
    class Reviewing {

        @Test
        @DisplayName("approving records who decided and when")
        void approveIsAttributed() {
            VerificationDocument document = submitted();

            document.approve(admin(), "NIC matches the profile name", REVIEWED_AT);

            assertThat(document.getStatus()).isEqualTo(DocumentStatus.APPROVED);
            assertThat(document.isApproved()).isTrue();
            assertThat(document.getReviewedBy()).isNotNull();
            assertThat(document.getReviewedAt()).isEqualTo(REVIEWED_AT);
            assertThat(document.getReviewNotes()).isEqualTo("NIC matches the profile name");
        }

        @Test
        @DisplayName("rejecting must say why, because the tutor has to know what to send instead")
        void rejectionNeedsAReason() {
            VerificationDocument document = submitted();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> document.reject(admin(), "   ", REVIEWED_AT));
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);

            document.reject(admin(), "The scan is too blurred to read", REVIEWED_AT);
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.REJECTED);
            assertThat(document.getReviewNotes()).isEqualTo("The scan is too blurred to read");
        }

        @Test
        @DisplayName("cannot be taken twice")
        void isFinal() {
            VerificationDocument document = submitted();
            document.approve(admin(), "Fine", REVIEWED_AT);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> document.reject(admin(), "Changed my mind", REVIEWED_AT))
                    .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("DOCUMENT_ALREADY_REVIEWED"));

            assertThat(document.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        }

        @Test
        @DisplayName("always has an author and a moment")
        void needsAReviewerAndATime() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> submitted().approve(null, "Fine", REVIEWED_AT));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> submitted().approve(admin(), "Fine", null));
        }

        @Test
        @DisplayName("truncates notes rather than letting them overflow the column")
        void boundsTheNotes() {
            VerificationDocument document = submitted();

            document.reject(admin(), "x".repeat(VerificationDocument.MAX_REVIEW_NOTES + 500), REVIEWED_AT);

            assertThat(document.getReviewNotes()).hasSize(VerificationDocument.MAX_REVIEW_NOTES);
        }
    }

    @Nested
    @DisplayName("withdrawing")
    class Withdrawing {

        @Test
        @DisplayName("is allowed while nobody has ruled on it")
        void allowedWhilePending() {
            assertThatCode(() -> submitted().ensureWithdrawable()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("is refused once a reviewer has, because the decision is an audit record")
        void refusedAfterAReview() {
            VerificationDocument approved = submitted();
            approved.approve(admin(), "Fine", REVIEWED_AT);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(approved::ensureWithdrawable)
                    .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("DOCUMENT_NOT_PENDING"));

            VerificationDocument rejected = submitted();
            rejected.reject(admin(), "Unreadable", REVIEWED_AT);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(rejected::ensureWithdrawable);
        }
    }

    @Test
    @DisplayName("belongs only to the tutor who sent it")
    void ownershipIsByTutorId() {
        VerificationDocument document = submitted();

        // No id outside a persistence context, so the honest assertion is that an unsaved
        // document matches nobody - least of all a caller guessing an id.
        assertThat(document.belongsTo(null)).isFalse();
        assertThat(document.belongsTo(42L)).isFalse();
    }

    @Test
    @DisplayName("refuses to exist without an owner, a type or a key")
    void requiresItsEssentials() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new VerificationDocument(null, DocumentType.NIC, STORAGE_KEY, upload(), content()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new VerificationDocument(tutor(), null, STORAGE_KEY, upload(), content()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new VerificationDocument(tutor(), DocumentType.NIC, "  ", upload(), content()));
    }

    private static VerificationDocument submitted() {
        return new VerificationDocument(tutor(), DocumentType.DEGREE_CERTIFICATE, STORAGE_KEY, upload(), content());
    }

    private static UploadedFile upload() {
        return new UploadedFile("degree.pdf", TestPdf.BYTES);
    }

    private static FileContent content() {
        return new FileContent(FileType.PDF, TestPdf.BYTES);
    }

    private static Tutor tutor() {
        return new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN);
    }

    private static Admin admin() {
        return new Admin("admin@tutorspoint.lk", "hash", "Reviewer", "+94770000000", Language.EN);
    }

    /** A small real PDF, so the recorded size and type are the ones a real upload would have. */
    private static final class TestPdf {
        private static final byte[] BYTES =
                "%PDF-1.4\ntrailer\n<<>>\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
