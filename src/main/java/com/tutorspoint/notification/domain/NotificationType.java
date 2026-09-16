package com.tutorspoint.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every notification the platform sends, each bound to the transport it belongs on and
 * the template that renders it. Keeping both here is what lets callers say <em>what</em>
 * happened ("send the OTP") and never <em>how</em> it travels — the
 * {@code NotificationChannelFactory} reads the channel off the type, and a caller
 * cannot pair a template with the wrong transport because it never gets to choose.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /** FR-A3: the click-to-confirm link sent after registration. */
    EMAIL_VERIFICATION(ChannelType.EMAIL, "email-verification"),

    /** FR-A6: the password-reset link. */
    PASSWORD_RESET(ChannelType.EMAIL, "password-reset"),

    /** FR-A2: the six-digit phone verification code. */
    PHONE_OTP(ChannelType.SMS, "otp"),

    /**
     * FR-E3: a parent has asked a tutor a question. Email rather than SMS deliberately —
     * this one carries a subject line, a name and a link, none of which fit in 160
     * characters, and unlike an OTP it is not urgent to the second.
     */
    ENQUIRY_RECEIVED(ChannelType.EMAIL, "enquiry-received"),

    /** FR-E2: the tutor has answered, and the thread is now open on both sides. */
    ENQUIRY_REPLIED(ChannelType.EMAIL, "enquiry-reply");

    /** The transport this notification is delivered over. */
    private final ChannelType channel;

    /**
     * Base file name of the template, resolved under
     * {@code templates/notifications/<language>/} with the extension the channel needs.
     */
    private final String templateKey;
}
