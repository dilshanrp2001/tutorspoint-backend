package com.tutorspoint.verification;

import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.AuthenticatedUser;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.InvalidUploadException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileStorage;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.StorageArea;
import com.tutorspoint.common.storage.TestFiles;
import com.tutorspoint.common.storage.UploadedFile;
import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.DocumentType;
import com.tutorspoint.verification.domain.VerificationDocument;
import com.tutorspoint.verification.event.DocumentViewedByAdminEvent;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Document submission and retrieval, with the store and the security context mocked.
 *
 * <p>The three tests the plan asks for are here in their unit form - an oversized file, a wrong
 * content type, and a non-owner attempting a download - and each asserts the same second thing:
 * that nothing was stored, or nothing was read, before the refusal.
 */
@ExtendWith(MockitoExtension.class)
class VerificationDocumentServiceImplTest {

    private static final Long TUTOR_ID = 7L;
    private static final Long OTHER_TUTOR_ID = 8L;
    private static final Long ADMIN_ID = 9L;
    private static final Long DOCUMENT_ID = 100L;
    private static final String STORAGE_KEY = "documents/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.pdf";

    @Mock
    private UserRepository users;

    @Mock
    private VerificationDocumentRepository documents;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private CurrentUser currentUser;

    private final VerificationMapper verificationMapper = new VerificationMapperImpl();

    @Mock
    private ApplicationEventPublisher events;

    private VerificationDocumentServiceImpl service;

    private Tutor tutor;

    @BeforeEach
    void setUp() {
        tutor = new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN);
        service = new VerificationDocumentServiceImpl(users, documents, fileStorage, verificationMapper, currentUser,
                events);
        lenient().when(currentUser.requireId()).thenReturn(TUTOR_ID);
    }

    @Test
    @DisplayName("a PDF is stored and recorded as pending, with the detected type rather than the declared one")
    void storesAndRecords() {
        givenTheCallerIsTheTutor();
        when(fileStorage.store(any(), any())).thenReturn(STORAGE_KEY);
        when(documents.save(any(VerificationDocument.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.upload(DocumentType.DEGREE_CERTIFICATE,
                new UploadedFile("my degree.pdf", TestFiles.pdf()));

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.documentType()).isEqualTo(DocumentType.DEGREE_CERTIFICATE);
        assertThat(response.originalFilename()).isEqualTo("my degree.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.reviewedAt()).isNull();

        // Captured rather than matched on equality: FileContent holds a byte[], and a record's
        // generated equals compares array references, not their contents.
        ArgumentCaptor<FileContent> stored = ArgumentCaptor.forClass(FileContent.class);
        verify(fileStorage).store(eq(StorageArea.TUTOR_DOCUMENTS), stored.capture());
        assertThat(stored.getValue().type()).isEqualTo(FileType.PDF);
        assertThat(stored.getValue().bytes()).isEqualTo(TestFiles.pdf());
    }

    @Test
    @DisplayName("a file the platform does not accept is refused, and nothing is written")
    void refusesAWrongContentType() {
        givenTheCallerIsTheTutor();

        UploadedFile video = new UploadedFile("clip.pdf", TestFiles.mp4());

        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> service.upload(DocumentType.OTHER, video))
                .satisfies(thrown -> assertThat(thrown.getCode())
                        .isEqualTo(InvalidUploadException.TYPE_NOT_ALLOWED));

        // The filename claimed .pdf. Nothing reached the store and nothing reached the table.
        verify(fileStorage, never()).store(any(), any());
        verify(documents, never()).save(any());
    }

    @Test
    @DisplayName("bytes of no recognised format at all are refused the same way")
    void refusesAnUnrecognisedFile() {
        givenTheCallerIsTheTutor();

        UploadedFile executable = new UploadedFile("cv.pdf", "MZ not a document".getBytes());

        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> service.upload(DocumentType.OTHER, executable));

        verify(fileStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("a file over the limit is refused, and nothing is written")
    void refusesAnOversizedFile() {
        givenTheCallerIsTheTutor();

        UploadedFile huge = new UploadedFile("scan.jpg", TestFiles.jpegOfAtLeast(5 * 1024 * 1024 + 1));

        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> service.upload(DocumentType.NIC, huge))
                .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo(InvalidUploadException.TOO_LARGE));

        verify(fileStorage, never()).store(any(), any());
        verify(documents, never()).save(any());
    }

    @Test
    @DisplayName("a tutor cannot fill the disk one document at a time")
    void refusesPastTheLimit() {
        when(currentUser.requireId()).thenReturn(TUTOR_ID);
        when(users.findById(TUTOR_ID)).thenReturn(Optional.of(tutor));
        when(documents.countByTutorId(TUTOR_ID))
                .thenReturn(VerificationDocumentServiceImpl.MAX_DOCUMENTS_PER_TUTOR);

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.upload(DocumentType.OTHER, new UploadedFile("x.pdf", TestFiles.pdf())))
                .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("TOO_MANY_DOCUMENTS"));

        verify(fileStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("an account that is not a tutor cannot submit documents")
    void refusesANonTutorAccount() {
        when(users.findById(TUTOR_ID)).thenReturn(Optional.of(
                new Parent("kamal@example.lk", "hash", "Kamal", "+94772000002", Language.SI)));

        assertThatExceptionOfType(UnauthorizedActionException.class)
                .isThrownBy(() -> service.upload(DocumentType.NIC, new UploadedFile("x.pdf", TestFiles.pdf())));
    }

    @Test
    @DisplayName("the list is scoped to the caller by the query, not by a check afterwards")
    void listsOnlyTheCallersDocuments() {
        when(documents.findByTutorIdOrderByCreatedAtDesc(TUTOR_ID)).thenReturn(List.of(pendingDocument()));

        var mine = service.myDocuments();

        assertThat(mine).hasSize(1);
        verify(documents).findByTutorIdOrderByCreatedAtDesc(TUTOR_ID);
    }

    @Test
    @DisplayName("withdrawing removes the row and the file")
    void withdrawingDeletesBoth() {
        VerificationDocument document = pendingDocument();
        when(documents.findByIdAndTutorId(DOCUMENT_ID, TUTOR_ID)).thenReturn(Optional.of(document));

        service.deleteMyDocument(DOCUMENT_ID);

        verify(documents).delete(document);
        verify(fileStorage).delete(STORAGE_KEY);
    }

    @Test
    @DisplayName("another tutor's document id is not found rather than forbidden")
    void cannotWithdrawSomebodyElsesDocument() {
        when(documents.findByIdAndTutorId(DOCUMENT_ID, TUTOR_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteMyDocument(DOCUMENT_ID));

        verify(fileStorage, never()).delete(any());
    }

    @Test
    @DisplayName("a reviewed document cannot be withdrawn, and its file stays put")
    void cannotWithdrawAReviewedDocument() {
        VerificationDocument document = pendingDocument();
        document.approve(admin(), "Fine", Instant.parse("2026-09-15T09:30:00Z"));
        when(documents.findByIdAndTutorId(DOCUMENT_ID, TUTOR_ID)).thenReturn(Optional.of(document));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.deleteMyDocument(DOCUMENT_ID))
                .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("DOCUMENT_NOT_PENDING"));

        verify(documents, never()).delete(any(VerificationDocument.class));
        verify(fileStorage, never()).delete(any());
    }

    @Test
    @DisplayName("the owner may download their own document")
    void theOwnerMayDownload() {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(TUTOR_ID, "kasun@example.lk", Role.TUTOR));
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(pendingDocument()));
        when(fileStorage.retrieve(STORAGE_KEY)).thenReturn(new FileContent(FileType.PDF, TestFiles.pdf()));

        DocumentDownload download = service.download(DOCUMENT_ID);

        assertThat(download.filename()).isEqualTo("degree.pdf");
        assertThat(download.content().bytes()).isEqualTo(TestFiles.pdf());
        // Reading your own document is not an audited event.
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an administrator may download anybody's, because that is the review")
    void anAdministratorMayDownload() {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(ADMIN_ID, "admin@tutorspoint.lk", Role.ADMIN));
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(pendingDocument()));
        when(fileStorage.retrieve(STORAGE_KEY)).thenReturn(new FileContent(FileType.PDF, TestFiles.pdf()));

        assertThat(service.download(DOCUMENT_ID).content().bytes()).isEqualTo(TestFiles.pdf());
        verify(events).publishEvent(new DocumentViewedByAdminEvent(ADMIN_ID, DOCUMENT_ID, TUTOR_ID));
    }

    @Test
    @DisplayName("anybody else is answered as not found, and the file is never read")
    void aStrangerGetsNotFound() {
        when(currentUser.require())
                .thenReturn(new AuthenticatedUser(OTHER_TUTOR_ID, "nimal@example.lk", Role.TUTOR));
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(pendingDocument()));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.download(DOCUMENT_ID));

        // Not merely refused after the fact: the store was never asked for the bytes, and a 404
        // rather than a 403 means the caller cannot even confirm the document exists.
        verify(fileStorage, never()).retrieve(any());
    }

    @Test
    @DisplayName("a parent holding a valid token is a stranger too")
    void aParentIsAStranger() {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(OTHER_TUTOR_ID, "niluka@example.lk", Role.PARENT));
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(pendingDocument()));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.download(DOCUMENT_ID));

        verify(fileStorage, never()).retrieve(any());
    }

    private void givenTheCallerIsTheTutor() {
        when(users.findById(TUTOR_ID)).thenReturn(Optional.of(tutor));
        when(documents.countByTutorId(TUTOR_ID)).thenReturn(0L);
    }

    /**
     * A pending document whose owner is the caller.
     *
     * <p>{@code belongsTo} compares the tutor's id, which is null on an unsaved entity, so the
     * ownership tests need a tutor that reports one - hence the anonymous subclass rather than a
     * bare constructor.
     */
    private VerificationDocument pendingDocument() {
        return new VerificationDocument(tutorWithId(), DocumentType.DEGREE_CERTIFICATE, STORAGE_KEY,
                new UploadedFile("degree.pdf", TestFiles.pdf()),
                new FileContent(FileType.PDF, TestFiles.pdf()));
    }

    private Tutor tutorWithId() {
        return new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN) {
            @Override
            public Long getId() {
                return TUTOR_ID;
            }
        };
    }

    private static Admin admin() {
        return new Admin("admin@tutorspoint.lk", "hash", "Reviewer", "+94770000000", Language.EN);
    }
}
