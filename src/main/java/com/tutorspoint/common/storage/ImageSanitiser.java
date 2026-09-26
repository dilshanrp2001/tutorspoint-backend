package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Turns an uploaded image into one the platform is willing to publish.
 *
 * <p>Two things happen, and the first is the important one. Decoding the pixels and encoding
 * them again produces a file that contains <strong>only</strong> pixels: every Exif tag goes
 * with it, and the tag that matters is the GPS position a phone camera writes into a
 * photograph by default. A profile photo is public, and publishing the coordinates of the
 * house a tutor teaches from - frequently a woman teaching from home - is a safety failure,
 * not a privacy footnote. Nothing about the upload path can be allowed to make that
 * conditional, so it happens to every image, always.
 *
 * <p>The second is size. A photograph straight off a phone is several thousand pixels wide and
 * will be displayed at a couple of hundred; capping the long edge saves the parent on a mobile
 * connection most of the download and costs the tutor nothing visible.
 *
 * <p>Re-encoding is also, incidentally, a robust format check: bytes that survive a decode and
 * an encode are a real image, whatever their first eight bytes claimed.
 */
@Slf4j
@Component
public class ImageSanitiser {

    /** Comfortably more than any layout uses, and a fraction of what a phone produces. */
    static final int MAX_EDGE_PIXELS = 1600;

    /**
     * A stripped, bounded copy of an image.
     *
     * @throws InvalidUploadException if the bytes cannot be decoded as an image after all
     */
    public FileContent sanitise(FileContent image) {
        if (!image.type().isImage()) {
            throw new IllegalArgumentException("Not an image: " + image.type());
        }
        BufferedImage decoded = decode(image);
        BufferedImage bounded = downscale(decoded);
        // Written in the format it arrived in: a PNG with transparency must not silently
        // become a JPEG with a black background.
        return new FileContent(image.type(), encode(bounded, image.type()));
    }

    private BufferedImage decode(FileContent image) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            if (decoded == null) {
                throw new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                        "The image could not be read");
            }
            return decoded;
        } catch (IOException e) {
            throw new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                    "The image could not be read");
        }
    }

    private BufferedImage downscale(BufferedImage source) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        if (longEdge <= MAX_EDGE_PIXELS) {
            // Still redrawn below, because the point of this class is that the output is a
            // fresh raster and never the original file.
            return redraw(source, source.getWidth(), source.getHeight());
        }
        double scale = (double) MAX_EDGE_PIXELS / longEdge;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        log.debug("Downscaling image from {}x{} to {}x{}", source.getWidth(), source.getHeight(), width, height);
        return redraw(source, width, height);
    }

    private BufferedImage redraw(BufferedImage source, int width, int height) {
        // TYPE_INT_ARGB keeps a transparent PNG transparent; the JPEG writer flattens it.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = target.createGraphics();
        try {
            graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image, FileType type) {
        BufferedImage writable = type == FileType.JPEG ? withoutAlpha(image) : image;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(writable, type.extension(), out)) {
                throw new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                        "The image could not be re-encoded");
            }
        } catch (IOException e) {
            throw new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                    "The image could not be re-encoded");
        }
        return out.toByteArray();
    }

    /** The JPEG writer cannot take an alpha channel; anything transparent becomes white. */
    private BufferedImage withoutAlpha(BufferedImage source) {
        BufferedImage opaque = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = opaque.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return opaque;
    }
}
