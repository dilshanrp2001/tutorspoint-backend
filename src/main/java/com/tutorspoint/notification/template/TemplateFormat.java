package com.tutorspoint.notification.template;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The two shapes a notification body comes in. Each channel asks for the one it can
 * deliver, which is why the same template key exists as both an {@code .html} and a
 * {@code .txt} file only when a notification genuinely goes out on both transports.
 */
@Getter
@RequiredArgsConstructor
public enum TemplateFormat {

    /** Rich body for email. */
    HTML(".html"),

    /** Plain body for SMS — no markup, and short, because every segment is billed. */
    TEXT(".txt");

    private final String extension;
}
