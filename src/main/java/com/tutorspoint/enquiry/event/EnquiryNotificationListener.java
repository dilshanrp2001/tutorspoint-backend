package com.tutorspoint.enquiry.event;

import com.tutorspoint.enquiry.config.EnquiryProperties;
import com.tutorspoint.notification.NotificationService;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the other side that something happened on their thread.
 *
 * <p>This class is the only thing in the enquiry feature that has heard of
 * {@link NotificationService}. The enquiry service publishes an event and returns; what
 * follows from that is decided here, and adding a consequence in a later phase means adding a
 * listener rather than editing the service that is already tested (Observer, architecture
 * section 7).
 *
 * <p><strong>{@code AFTER_COMMIT} is how FR-E3 is enforced, not a performance choice.</strong>
 * "Every enquiry is persisted before any notification fires" is not a rule anybody can
 * remember to follow at each call site; here it is structural. A listener bound to this phase
 * cannot run until the row is committed, and a transaction that rolls back sends nothing —
 * so there is no path that emails a tutor about an enquiry which does not exist.
 *
 * <p>Delivery itself is fire-and-forget inside {@code NotificationService}: an SMTP server
 * that is down is logged, never thrown back at a request that has already succeeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnquiryNotificationListener {

    private final NotificationService notifications;
    private final EnquiryProperties properties;

    /** A tutor has an enquiry waiting. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnquiryCreated(EnquiryCreatedEvent event) {
        log.debug("Notifying tutor {} of enquiry {}", event.tutorId(), event.enquiryId());
        notifications.send(Notification.builder()
                .type(NotificationType.ENQUIRY_RECEIVED)
                .recipient(event.tutorEmail())
                .language(event.tutorLanguage())
                .variable("tutorName", event.tutorName())
                // The parent's name, and nothing else about them. A notification is sent by the
                // platform to one of its own users; it is not the reveal, and it must not
                // become a way around it.
                .variable("parentName", event.parentName())
                .variable("subjectName", event.subjectName())
                .variable("threadUrl", properties.threadUrlFor(event.enquiryId()))
                .build());
    }

    /** A parent has been answered. Fires on the tutor's first reply only. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnquiryResponded(EnquiryRespondedEvent event) {
        log.debug("Notifying parent {} that enquiry {} was answered", event.parentId(), event.enquiryId());
        notifications.send(Notification.builder()
                .type(NotificationType.ENQUIRY_REPLIED)
                .recipient(event.parentEmail())
                .language(event.parentLanguage())
                .variable("parentName", event.parentName())
                .variable("tutorName", event.tutorName())
                .variable("threadUrl", properties.threadUrlFor(event.enquiryId()))
                .build());
    }
}
