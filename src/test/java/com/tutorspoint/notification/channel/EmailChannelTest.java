package com.tutorspoint.notification.channel;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.notification.NotificationDeliveryException;
import com.tutorspoint.notification.config.NotificationEmailProperties;
import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import com.tutorspoint.notification.template.NotificationTemplateRenderer;
import com.tutorspoint.notification.template.TemplateFormat;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Composition and failure handling of the SMTP channel, with the sender mocked out. */
@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationTemplateRenderer renderer;

    private EmailChannel channel;

    private static final Notification NOTIFICATION = Notification.builder()
            .type(NotificationType.EMAIL_VERIFICATION)
            .recipient("nimal@example.com")
            .language(Language.SI)
            .variable("fullName", "Nimal Perera")
            .build();

    @BeforeEach
    void setUp() {
        channel = new EmailChannel(mailSender, renderer,
                new NotificationEmailProperties("no-reply@tutorspoint.xyz", "TutorsPoint"));
    }

    @Test
    void itAnswersForTheEmailTransport() {
        assertThat(channel.supports()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void itSendsTheRenderedHtmlWithTheTranslatedSubject() throws Exception {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        given(renderer.subject(NOTIFICATION)).willReturn("සත්‍යාපනය කරන්න");
        given(renderer.render(NOTIFICATION, TemplateFormat.HTML)).willReturn("<p>Hello Nimal</p>");

        channel.send(NOTIFICATION);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();

        assertThat(message.getSubject()).isEqualTo("සත්‍යාපනය කරන්න");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("nimal@example.com");
        assertThat((InternetAddress) message.getFrom()[0])
                .satisfies(from -> {
                    assertThat(from.getAddress()).isEqualTo("no-reply@tutorspoint.xyz");
                    assertThat(from.getPersonal()).isEqualTo("TutorsPoint");
                });
        assertThat(message.getContent()).isEqualTo("<p>Hello Nimal</p>");
        // Read off the data handler: headers are only written on saveChanges(), which
        // JavaMailSender does at send time and the mock never reaches.
        assertThat(message.getDataHandler().getContentType()).contains("text/html").contains("UTF-8");
    }

    @Test
    void aRenderingFailureIsReportedAsADeliveryFailureAndNothingIsSent() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        given(renderer.subject(NOTIFICATION)).willThrow(new IllegalStateException("no bundle"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> channel.send(NOTIFICATION));

        then(mailSender).should(never()).send(any(MimeMessage.class));
    }

    @Test
    void anSmtpRefusalBecomesANotificationDeliveryException() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        given(renderer.subject(NOTIFICATION)).willReturn("Verify");
        given(renderer.render(NOTIFICATION, TemplateFormat.HTML)).willReturn("<p>Hello</p>");
        willThrow(new MailSendException("mailbox full")).given(mailSender).send(any(MimeMessage.class));

        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> channel.send(NOTIFICATION))
                // The recipient is masked even in the exception message, which gets logged.
                .withMessageContaining("n***@example.com")
                .withMessageNotContaining("nimal@example.com");
    }
}
