package com.tutorspoint.notification;

import com.tutorspoint.common.exception.TutorsPointException;

/**
 * A channel could not hand the message to its transport — SMTP refused it, the SMS
 * gateway returned an error, the template would not render.
 *
 * <p>This never reaches a client: {@code NotificationServiceImpl} catches and logs it,
 * because a failed notification must not roll back the registration that triggered it.
 * It exists so channels and adapters fail in one recognisable way rather than leaking
 * {@code MessagingException} or a vendor's own exception type upwards.
 */
public class NotificationDeliveryException extends TutorsPointException {

    private static final String CODE = "NOTIFICATION_DELIVERY_FAILED";

    public NotificationDeliveryException(String message) {
        super(CODE, message);
    }

    public NotificationDeliveryException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
