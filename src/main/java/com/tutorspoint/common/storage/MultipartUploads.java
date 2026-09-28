package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * The boundary where a multipart part becomes an {@link UploadedFile}.
 *
 * <p>Deliberately the only place in the application that mentions {@link MultipartFile}
 * outside a controller signature. Every upload endpoint needs the same three lines - reject an
 * empty part, read the bytes, turn a read failure into a 400 rather than a server error - and
 * a copy of them in each controller is a copy that can be written slightly differently.
 *
 * <p>It lives in {@code common.storage} rather than in a feature package because uploads
 * belong to no one feature, and it is used only by controllers, which is what keeps the servlet
 * type from travelling any further down.
 */
public final class MultipartUploads {

    private MultipartUploads() {
    }

    /**
     * Reads a multipart part.
     *
     * @throws InvalidUploadException if the part is absent, empty, or could not be read - a
     *                                dropped connection mid-upload is the usual cause, and none
     *                                of them is a fault on our side
     */
    public static UploadedFile read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException(InvalidUploadException.EMPTY, "No file was uploaded");
        }
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidUploadException(InvalidUploadException.EMPTY, "The uploaded file could not be read");
        }
    }
}
