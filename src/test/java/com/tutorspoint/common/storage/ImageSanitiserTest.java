package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The metadata rule, which is a safety rule rather than a hygiene one.
 *
 * <p>{@link #stripsExifMetadata()} is the test that matters: a phone writes the GPS position of
 * the room the photograph was taken in into the file, a profile photo is public, and a great
 * many tutors teach from home.
 */
class ImageSanitiserTest {

    private final ImageSanitiser sanitiser = new ImageSanitiser();

    @Test
    @DisplayName("a photograph comes out of the sanitiser with its Exif block gone")
    void stripsExifMetadata() {
        String gpsMarker = "GPS-6.9271-79.8612-HOME-ADDRESS";
        byte[] fromAPhone = TestFiles.jpegWithExif(gpsMarker);

        // The marker really is in the file that was uploaded, or the test proves nothing.
        assertThat(asText(fromAPhone)).contains(gpsMarker).contains("Exif");

        FileContent cleaned = sanitiser.sanitise(new FileContent(FileType.JPEG, fromAPhone));

        assertThat(asText(cleaned.bytes())).doesNotContain(gpsMarker);
        assertThat(asText(cleaned.bytes())).doesNotContain("Exif");
    }

    @Test
    @DisplayName("the picture survives: same format, same shape, still decodable")
    void keepsTheImageItself() throws IOException {
        FileContent cleaned = sanitiser.sanitise(new FileContent(FileType.JPEG, TestFiles.jpeg()));

        assertThat(cleaned.type()).isEqualTo(FileType.JPEG);
        assertThat(FileType.detect(cleaned.bytes())).contains(FileType.JPEG);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(30);
    }

    @Test
    @DisplayName("a photograph off a modern phone is scaled down to something worth downloading")
    void capsTheLongestEdge() throws IOException {
        byte[] huge = TestFiles.image("jpg", 4000, 3000);

        FileContent cleaned = sanitiser.sanitise(new FileContent(FileType.JPEG, huge));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(ImageSanitiser.MAX_EDGE_PIXELS);
        // The aspect ratio is kept: 4000x3000 is 4:3, so 1600 wide is 1200 tall.
        assertThat(decoded.getHeight()).isEqualTo(1200);
        assertThat(cleaned.sizeBytes()).isLessThan(huge.length);
    }

    @Test
    @DisplayName("an image already small enough is left at its own size")
    void doesNotUpscale() throws IOException {
        FileContent cleaned = sanitiser.sanitise(new FileContent(FileType.PNG, TestFiles.png()));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(cleaned.type()).isEqualTo(FileType.PNG);
    }

    @Test
    @DisplayName("bytes that begin like an image but are not one are refused, not stored")
    void refusesSomethingThatOnlyLooksLikeAnImage() {
        // A valid PNG signature followed by nothing that decodes. The signature check passed;
        // the decode is the second, harder gate.
        byte[] fake = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> sanitiser.sanitise(new FileContent(FileType.PNG, fake)))
                .satisfies(thrown -> assertThat(thrown.getCode())
                        .isEqualTo(InvalidUploadException.TYPE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("refuses to be handed something that is not an image at all")
    void refusesANonImage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sanitiser.sanitise(new FileContent(FileType.PDF, TestFiles.pdf())));
    }

    /** Byte-for-character, so a search for a marker inside binary content is exact. */
    private static String asText(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
