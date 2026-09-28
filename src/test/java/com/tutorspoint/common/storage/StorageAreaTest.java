package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The upload policy, which is the whole reason these constants are on an enum rather than
 * scattered through the services that upload.
 */
class StorageAreaTest {

    private static final long FIVE_MB = 5L * 1024 * 1024;

    @Test
    @DisplayName("documents take a PDF or a photograph of a certificate")
    void documentsAcceptWhatATutorActuallyHas() {
        assertThatCode(() -> StorageArea.TUTOR_DOCUMENTS.ensureAccepts(FileType.PDF, 1024))
                .doesNotThrowAnyException();
        assertThatCode(() -> StorageArea.TUTOR_DOCUMENTS.ensureAccepts(FileType.JPEG, 1024))
                .doesNotThrowAnyException();
        assertThatCode(() -> StorageArea.TUTOR_DOCUMENTS.ensureAccepts(FileType.PNG, 1024))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a video is not a qualification document, and a PDF is not a profile photo")
    void eachAreaRefusesWhatDoesNotBelongInIt() {
        assertRejects(StorageArea.TUTOR_DOCUMENTS, FileType.MP4, 1024, InvalidUploadException.TYPE_NOT_ALLOWED);
        assertRejects(StorageArea.PROFILE_PHOTOS, FileType.PDF, 1024, InvalidUploadException.TYPE_NOT_ALLOWED);
        assertRejects(StorageArea.INTRO_VIDEOS, FileType.JPEG, 1024, InvalidUploadException.TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("a file over five megabytes is refused, whatever it is")
    void refusesAnOversizedFile() {
        assertRejects(StorageArea.TUTOR_DOCUMENTS, FileType.PDF, FIVE_MB + 1, InvalidUploadException.TOO_LARGE);
        assertRejects(StorageArea.PROFILE_PHOTOS, FileType.JPEG, FIVE_MB + 1, InvalidUploadException.TOO_LARGE);

        // The limit itself is allowed: a cap is inclusive, or the message is a lie.
        assertThatCode(() -> StorageArea.TUTOR_DOCUMENTS.ensureAccepts(FileType.PDF, FIVE_MB))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty file is refused before its type is even considered")
    void refusesAnEmptyFile() {
        assertRejects(StorageArea.TUTOR_DOCUMENTS, FileType.PDF, 0, InvalidUploadException.EMPTY);
    }

    @Test
    @DisplayName("an unrecognised type is refused rather than allowed through as null")
    void refusesAnUnknownType() {
        assertRejects(StorageArea.PROFILE_PHOTOS, null, 1024, InvalidUploadException.TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("documents are private and media is public - the fact the media endpoint filters on")
    void visibilityIsPartOfTheArea() {
        assertThat(StorageArea.TUTOR_DOCUMENTS.isPublic()).isFalse();
        assertThat(StorageArea.PROFILE_PHOTOS.isPublic()).isTrue();
        assertThat(StorageArea.INTRO_VIDEOS.isPublic()).isTrue();
    }

    @Test
    @DisplayName("advertises what it accepts, for the API description and the file picker")
    void listsItsAcceptedTypes() {
        assertThat(StorageArea.PROFILE_PHOTOS.acceptedContentTypes()).isEqualTo("image/jpeg, image/png");
        assertThat(StorageArea.TUTOR_DOCUMENTS.maxBytes()).isEqualTo(FIVE_MB);
    }

    private static void assertRejects(StorageArea area, FileType type, long sizeBytes, String expectedCode) {
        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> area.ensureAccepts(type, sizeBytes))
                .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo(expectedCode));
    }
}
