package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The disk implementation, against a real temporary directory.
 *
 * <p>A fixed clock, so the dated part of a key is an assertion rather than a guess, and a
 * {@code @TempDir} rather than a mocked filesystem: what is being tested is that bytes survive
 * a round trip and that a hostile key cannot reach outside the root, and neither claim means
 * anything against a fake.
 */
class LocalDiskFileStorageTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-12T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    private LocalDiskFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskFileStorage(new LocalDiskStorageProperties(root), FIXED);
    }

    @Test
    @DisplayName("stores bytes and reads back exactly what went in")
    void roundTrips() {
        FileContent content = new FileContent(FileType.PDF, TestFiles.pdf());

        String key = storage.store(StorageArea.TUTOR_DOCUMENTS, content);
        FileContent read = storage.retrieve(key);

        assertThat(read.bytes()).isEqualTo(TestFiles.pdf());
        assertThat(read.type()).isEqualTo(FileType.PDF);
        assertThat(read.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("names the file itself: the area, the date, a random name and the real extension")
    void generatesAnOpaqueKey() {
        String key = storage.store(StorageArea.TUTOR_DOCUMENTS, new FileContent(FileType.PDF, TestFiles.pdf()));

        assertThat(key).startsWith("documents/2026/09/").endsWith(".pdf");
        assertThat(StorageKeys.parse(key)).isPresent();
        assertThat(Files.exists(root.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("the same bytes stored twice are two files, not one shared between two owners")
    void neverDeduplicates() {
        FileContent content = new FileContent(FileType.PDF, TestFiles.pdf());

        String first = storage.store(StorageArea.TUTOR_DOCUMENTS, content);
        String second = storage.store(StorageArea.TUTOR_DOCUMENTS, content);

        assertThat(first).isNotEqualTo(second);
        assertThat(storage.retrieve(first).bytes()).isEqualTo(storage.retrieve(second).bytes());
    }

    @Test
    @DisplayName("each area keeps its own files, which is what the media endpoint filters on")
    void separatesTheAreas() {
        String document = storage.store(StorageArea.TUTOR_DOCUMENTS, new FileContent(FileType.PDF, TestFiles.pdf()));
        String photo = storage.store(StorageArea.PROFILE_PHOTOS, new FileContent(FileType.JPEG, TestFiles.jpeg()));

        assertThat(StorageKeys.require(document).area()).isEqualTo(StorageArea.TUTOR_DOCUMENTS);
        assertThat(StorageKeys.require(photo).area()).isEqualTo(StorageArea.PROFILE_PHOTOS);
    }

    @Test
    @DisplayName("deleting removes the file, and deleting again is not an error")
    void deleteIsIdempotent() {
        String key = storage.store(StorageArea.TUTOR_DOCUMENTS, new FileContent(FileType.PDF, TestFiles.pdf()));

        storage.delete(key);

        assertThat(Files.exists(root.resolve(key))).isFalse();
        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a key that names nothing reads as not found, not as a server error")
    void missingContentIsNotFound() {
        String key = "documents/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.pdf";

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> storage.retrieve(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../../etc/passwd",
            "documents/../../../etc/passwd",
            "documents/2026/09/../../../../secret.pdf",
            "/etc/passwd",
            "documents/2026/09/not-a-uuid.pdf",
            "documents/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.exe",
            "windows\\system32\\config",
            ""
    })
    @DisplayName("a key that is not one we could have issued never becomes a path")
    void refusesEveryKeyItDidNotIssue(String hostileKey) {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> storage.retrieve(hostileKey));
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> storage.delete(hostileKey));
    }

    @Test
    @DisplayName("nothing outside the root is touched, even by a delete")
    void neverReachesOutsideTheRoot() throws IOException {
        Path outside = root.getParent().resolve("please-do-not-delete-me.txt");
        Files.write(outside, List.of("still here"));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> storage.delete("../" + outside.getFileName()));

        assertThat(Files.exists(outside)).isTrue();
        Files.deleteIfExists(outside);
    }

    @Test
    @DisplayName("creates its root on startup, so a fresh deployment needs no manual step")
    void createsTheRoot() {
        Path fresh = root.resolve("nested/created/on/demand");

        new LocalDiskFileStorage(new LocalDiskStorageProperties(fresh), FIXED);

        assertThat(Files.isDirectory(fresh)).isTrue();
    }
}
