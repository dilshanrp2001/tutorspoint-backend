package com.tutorspoint.verification;

import com.tutorspoint.common.storage.FileContent;

/**
 * A document's bytes together with the name to offer them under.
 *
 * <p>Two values rather than one because the filename is not a property of the content: the
 * store knows only an opaque key, and the name the tutor recognises lives on the row. The
 * controller needs both to write a {@code Content-Disposition}, and neither belongs to the
 * other.
 */
public record DocumentDownload(FileContent content, String filename) {
}
