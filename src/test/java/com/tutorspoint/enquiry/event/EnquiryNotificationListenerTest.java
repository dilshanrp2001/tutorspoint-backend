package com.tutorspoint.enquiry.event;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.enquiry.config.EnquiryProperties;
import com.tutorspoint.notification.NotificationService;
import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

/**
 * The notification half of the Observer wiring: given an event, the right message goes to the
 * right person in the right language, with a link they can follow.
 *
 * <p>Unit-level, because the interesting question here is what the listener builds, not that
 * Spring delivers the event — {@code EnquiryIT} covers the delivery.
 */
@ExtendWith(MockitoExtension.class)
class EnquiryNotificationListenerTest {

    private static final EnquiryProperties PROPERTIES =
            new EnquiryProperties(10, "https://tutorspoint.lk/enquiries");

    @Mock
    private NotificationService notifications;

    @Test
    @DisplayName("a new enquiry emails the tutor, in the tutor's language, with a link to the thread")
    void notifiesTheTutorOfANewEnquiry() {
        listener().onEnquiryCreated(new EnquiryCreatedEvent(
                77L, 1L, "Niluka", 2L, "kasun@example.lk", "Kasun", Language.SI, "රසායන විද්‍යාව"));

        Notification sent = captureSent();
        assertThat(sent.getType()).isEqualTo(NotificationType.ENQUIRY_RECEIVED);
        assertThat(sent.channelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(sent.getRecipient()).isEqualTo("kasun@example.lk");
        assertThat(sent.getLanguage()).isEqualTo(Language.SI);
        assertThat(sent.getVariables())
                .containsEntry("tutorName", "Kasun")
                .containsEntry("seekerName", "Niluka")
                .containsEntry("subjectName", "රසායන විද්‍යාව")
                .containsEntry("threadUrl", "https://tutorspoint.lk/enquiries/77");
    }

    @Test
    @DisplayName("the tutor's notification carries the parent's name and no way to reach them")
    void theNotificationIsNotItselfAReveal() {
        listener().onEnquiryCreated(new EnquiryCreatedEvent(
                77L, 1L, "Niluka", 2L, "kasun@example.lk", "Kasun", Language.EN, "Chemistry"));

        assertThat(captureSent().getVariables().values())
                .noneMatch(value -> String.valueOf(value).contains("@example.lk")
                        || String.valueOf(value).matches(".*\\d{9,}.*"));
    }

    @Test
    @DisplayName("a reply emails the parent, in the parent's language")
    void notifiesTheParentOfAReply() {
        listener().onEnquiryResponded(new EnquiryRespondedEvent(
                77L, 1L, "niluka@example.lk", "Niluka", Language.TA, 2L, "Kasun"));

        Notification sent = captureSent();
        assertThat(sent.getType()).isEqualTo(NotificationType.ENQUIRY_REPLIED);
        assertThat(sent.getRecipient()).isEqualTo("niluka@example.lk");
        assertThat(sent.getLanguage()).isEqualTo(Language.TA);
        assertThat(sent.getVariables())
                .containsEntry("seekerName", "Niluka")
                .containsEntry("tutorName", "Kasun")
                .containsEntry("threadUrl", "https://tutorspoint.lk/enquiries/77");
    }

    private EnquiryNotificationListener listener() {
        return new EnquiryNotificationListener(notifications, PROPERTIES);
    }

    private Notification captureSent() {
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        then(notifications).should().send(sent.capture());
        return sent.getValue();
    }
}
