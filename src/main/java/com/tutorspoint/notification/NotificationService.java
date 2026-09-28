package com.tutorspoint.notification;

import com.tutorspoint.notification.domain.Notification;

/**
 * The one way the rest of the application sends anything to a user. Nothing outside
 * this package touches SMTP, a mail sender, or an SMS gateway; callers describe the
 * message and this module decides how it travels.
 */
public interface NotificationService {

    /**
     * Delivers the notification on the transport its type declares.
     *
     * <p>Fire-and-forget: the call returns immediately, delivery happens on another
     * thread, and a failure is logged rather than thrown. A caller inside a transaction
     * is therefore never rolled back because a gateway was down — registration must
     * survive an unsent SMS.
     */
    void send(Notification notification);
}
