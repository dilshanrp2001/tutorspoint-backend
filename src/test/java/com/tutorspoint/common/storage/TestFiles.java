package com.tutorspoint.common.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Files for tests to upload.
 *
 * <p>Real ones, not fixtures full of zeroes: the images are encoded by {@code ImageIO}, so a
 * test that puts one through {@link ImageSanitiser} is exercising a decode and an encode rather
 * than a stub. The PDF and MP4 are the smallest byte sequences that are honestly of that
 * format for the purpose the platform uses - the leading signature - which is what the
 * detector reads and all it reads.
 *
 * <p>Public because uploads are exercised from more than one test package - the profile photo
 * path in {@code tutor} and the document path in {@code verification} both need the same real
 * files, and two copies of this would drift.
 */
public final class TestFiles {

    private TestFiles() {
    }

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] jpeg() {
        return image("jpg", 40, 30);
    }

    public static byte[] png() {
        return image("png", 40, 30);
    }

    /** An ISO base media container: a size field, then {@code ftyp}. */
    public static byte[] mp4() {
        byte[] header = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        return ByteBuffer.allocate(header.length + 12).put(header).array();
    }

    /** An encoded image of a given size, for the downscaling rule. */
    public static byte[] image(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * A JPEG carrying an Exif block, which is what a photograph off a phone looks like.
     *
     * <p>The block is spliced in directly after the start-of-image marker, exactly where a
     * camera writes it. {@code marker} is a recognisable string inside the payload, so a test
     * can assert on the sanitised output that the metadata is not merely rewritten but gone.
     */
    public static byte[] jpegWithExif(String marker) {
        byte[] jpeg = jpeg();
        // "Exif", two NUL bytes, then the marker - the header a camera writes.
        byte[] payload = ("Exif" + "\0\0" + marker).getBytes(StandardCharsets.ISO_8859_1);
        int segmentLength = payload.length + 2;

        return ByteBuffer.allocate(jpeg.length + payload.length + 4)
                // SOI
                .put(jpeg, 0, 2)
                // APP1, its length, then the payload
                .put((byte) 0xFF).put((byte) 0xE1)
                .put((byte) (segmentLength >> 8)).put((byte) segmentLength)
                .put(payload)
                // the rest of the original image
                .put(jpeg, 2, jpeg.length - 2)
                .array();
    }

    /** A file of a given size, still a real JPEG at the front, for the size caps. */
    public static byte[] jpegOfAtLeast(int bytes) {
        byte[] jpeg = jpeg();
        byte[] padded = new byte[Math.max(bytes, jpeg.length)];
        System.arraycopy(jpeg, 0, padded, 0, jpeg.length);
        return padded;
    }
}
