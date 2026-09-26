package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Serves the public files: profile photographs and introduction videos.
 *
 * <p>It lives in {@code common} rather than in a feature package because the files it serves
 * belong to more than one feature and none of them owns the address space.
 *
 * <p><strong>Public means public here, so the one rule that matters is what this refuses.</strong>
 * A key naming a private area is answered as not found, not as forbidden: qualification
 * documents are reachable only through {@code VerificationDocumentController}, which
 * authorises every read, and this endpoint must not even confirm that a given document key
 * exists. A key that is malformed gets the same answer, which also means the traversal attempt
 * that produced it learns nothing.
 *
 * <p>Returning the bytes as a {@code byte[]} body rather than a stream is the same trade the
 * storage interface makes: every area is capped at a few megabytes, and in exchange there is
 * no resource to leak on an aborted request.
 */
@RestController
@RequestMapping(MediaUrls.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Media", description = "Public profile photographs and introduction videos")
public class MediaController {

    /**
     * A year. Safe because a key is immutable by construction - changing a photo produces a
     * new key and a new URL, so a cached response can never be the wrong one.
     */
    private static final Duration CACHE_TTL = Duration.ofDays(365);

    private final FileStorage fileStorage;

    @GetMapping("**")
    @Operation(summary = "Fetch a public file",
            description = "Profile photographs and introduction videos. Private files are not "
                    + "served here and answer as not found.")
    public ResponseEntity<byte[]> media(HttpServletRequest request) {
        String storageKey = keyFrom(request);
        StorageKeys.ParsedKey key = StorageKeys.parse(storageKey)
                .filter(parsed -> parsed.area().isPublic())
                .orElseThrow(() -> new ResourceNotFoundException("Media", storageKey));

        FileContent content = fileStorage.retrieve(key.value());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic().immutable())
                // Nothing here is markup, and a browser must never be talked into thinking it is.
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }

    /**
     * The part of the path after the base, decoded once.
     *
     * <p>A key contains slashes, so it cannot be a single {@code @PathVariable}. Reading the
     * remainder from the request is the standard way to take a path-shaped variable, and it is
     * safe here for the same reason the rest of this class is: whatever comes out is handed
     * straight to {@link StorageKeys#parse}, which accepts only the exact shape we issue.
     */
    private static String keyFrom(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String remainder = path.length() > MediaUrls.BASE_PATH.length()
                ? path.substring(MediaUrls.BASE_PATH.length())
                : "";
        return UriUtils.decode(remainder, StandardCharsets.UTF_8);
    }
}
