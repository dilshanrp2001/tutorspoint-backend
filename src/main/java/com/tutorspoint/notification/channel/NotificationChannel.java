package com.tutorspoint.notification.channel;

import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;

/**
 * A transport a notification can be delivered over. The rest of the application never
 * sees an implementation of this: it goes through {@code NotificationService}, which
 * asks {@link NotificationChannelFactory} for the right one.
 */
public interface NotificationChannel {

    /** The channel type this implementation answers for. Exactly one per type. */
    ChannelType supports();

    /**
     * Renders and delivers the message. Synchronous and blocking — the asynchrony lives
     * in the service, so a channel stays a plain, directly testable object.
     *
     * @throws com.tutorspoint.notification.NotificationDeliveryException if the
     *         transport rejects the message
     */
    void send(Notification notification);
}
