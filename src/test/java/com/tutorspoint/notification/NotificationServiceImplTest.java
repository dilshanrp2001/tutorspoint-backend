package com.tutorspoint.notification;

import com.tutorspoint.notification.channel.NotificationChannel;
import com.tutorspoint.notification.channel.NotificationChannelFactory;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * The one guarantee callers rely on: sending a notification cannot break what they
 * were doing. Registration must still succeed when the SMS gateway is down.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationChannelFactory channelFactory;

    @Mock
    private NotificationChannel channel;

    @InjectMocks
    private NotificationServiceImpl service;

    private static final Notification OTP = Notification.builder()
            .type(NotificationType.PHONE_OTP)
            .recipient("+94771234567")
            .variable("code", "123456")
            .build();

    @Test
    void itDelegatesToTheChannelTheFactoryChose() {
        given(channelFactory.channelFor(NotificationType.PHONE_OTP)).willReturn(channel);

        service.send(OTP);

        then(channel).should().send(OTP);
    }

    @Test
    void aChannelFailureIsLoggedAndNotRethrownAtTheCaller() {
        given(channelFactory.channelFor(NotificationType.PHONE_OTP)).willReturn(channel);
        willThrow(new NotificationDeliveryException("gateway down")).given(channel).send(OTP);

        assertThatCode(() -> service.send(OTP)).doesNotThrowAnyException();
    }

    @Test
    void aMisconfiguredChannelDoesNotBreakTheCallerEither() {
        given(channelFactory.channelFor(NotificationType.PHONE_OTP))
                .willThrow(new IllegalStateException("no SMS channel registered"));

        assertThatCode(() -> service.send(OTP)).doesNotThrowAnyException();
    }
}
