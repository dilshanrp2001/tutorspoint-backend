package com.tutorspoint.notification.domain;

import com.tutorspoint.common.domain.Language;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

/**
 * One message to send: who it goes to, what it says, and in which language. An
 * immutable value object, built through its {@link NotificationBuilder} because the
 * parameter list is long enough that positional arguments stop being readable (Builder).
 *
 * <pre>{@code
 * Notification.builder()
 *         .type(NotificationType.PHONE_OTP)
 *         .recipient("+94771234567")
 *         .language(user.getPreferredLanguage())
 *         .variable("code", "123456")
 *         .variable("expiryMinutes", 5)
 *         .build();
 * }</pre>
 *
 * <p>The channel type and template key are not settable: they are read off
 * {@link NotificationType}, so a caller cannot post an OTP template to an email
 * address or invent a template that has no translations.
 */
@Getter
@EqualsAndHashCode
public final class Notification {

    private final NotificationType type;

    /** Email address or E.164 phone number, depending on the type's channel. */
    private final String recipient;

    /** The recipient's preferred language; the renderer falls back to EN if untranslated. */
    private final Language language;

    /** Values the template interpolates. Never logged — an OTP code lives in here. */
    private final Map<String, Object> variables;

    @Builder(builderClassName = "NotificationBuilder")
    private Notification(NotificationType type,
                         String recipient,
                         Language language,
                         @Singular("variable") Map<String, Object> variables) {
        this.type = require(type, "type");
        this.recipient = requireText(recipient, "recipient");
        this.language = language == null ? Language.EN : language;
        this.variables = variables;
    }

    public ChannelType channelType() {
        return type.getChannel();
    }

    public String templateKey() {
        return type.getTemplateKey();
    }

    /**
     * The recipient with its identifying middle removed, for log lines. Delivery logs
     * are useful for support and must not turn the application log into a contact list.
     */
    public String maskedRecipient() {
        int at = recipient.indexOf('@');
        if (at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        int keep = Math.min(3, recipient.length());
        return "***" + recipient.substring(recipient.length() - keep);
    }

    /** Deliberately omits {@link #variables}: they carry secrets. */
    @Override
    public String toString() {
        return "Notification[type=%s, recipient=%s, language=%s]"
                .formatted(type, maskedRecipient(), language);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
