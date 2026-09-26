package com.tutorspoint.notification.channel;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.notification.NotificationDeliveryException;
import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import com.tutorspoint.notification.sms.SmsProvider;
import com.tutorspoint.notification.template.NotificationTemplateRenderer;
import com.tutorspoint.notification.template.TemplateFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/** The SMS channel renders and delegates — it knows nothing about any gateway. */
@ExtendWith(MockitoExtension.class)
class SmsChannelTest {

    @Mock
    private SmsProvider smsProvider;

    @Mock
    private NotificationTemplateRenderer renderer;

    @InjectMocks
    private SmsChannel channel;

    private static final Notification OTP = Notification.builder()
            .type(NotificationType.PHONE_OTP)
            .recipient("+94771234567")
            .language(Language.TA)
            .variable("code", "123456")
            .build();

    @Test
    void itAnswersForTheSmsTransport() {
        assertThat(channel.supports()).isEqualTo(ChannelType.SMS);
    }

    @Test
    void itHandsTheRenderedTextToTheProviderWithoutTemplateWhitespace() {
        given(renderer.render(OTP, TemplateFormat.TEXT)).willReturn("\nUnga kuriyeedu: 123456\n");

        channel.send(OTP);

        then(smsProvider).should().send("+94771234567", "Unga kuriyeedu: 123456");
    }

    @Test
    void aRenderingFailureStopsShortOfSpendingMoneyOnASend() {
        given(renderer.render(OTP, TemplateFormat.TEXT)).willThrow(new IllegalStateException("no template"));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> channel.send(OTP));

        then(smsProvider).shouldHaveNoInteractions();
    }

    @Test
    void aGatewayFailureIsNotSwallowedHere() {
        given(renderer.render(OTP, TemplateFormat.TEXT)).willReturn("code 123456");
        willThrow(new NotificationDeliveryException("gateway down"))
                .given(smsProvider).send("+94771234567", "code 123456");

        // Swallowing is NotificationServiceImpl's job, at the edge of the async task;
        // a channel that hid failures would make delivery problems invisible.
        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> channel.send(OTP));
    }
}
