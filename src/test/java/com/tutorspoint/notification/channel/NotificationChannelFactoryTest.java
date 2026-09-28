package com.tutorspoint.notification.channel;

import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Factory selection: the type decides the transport, and misconfiguration is loud. */
class NotificationChannelFactoryTest {

    private final NotificationChannel emailChannel = new StubChannel(ChannelType.EMAIL);
    private final NotificationChannel smsChannel = new StubChannel(ChannelType.SMS);

    @Test
    void emailNotificationsGoToTheEmailChannel() {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(emailChannel, smsChannel));

        assertThat(factory.channelFor(NotificationType.EMAIL_VERIFICATION)).isSameAs(emailChannel);
        assertThat(factory.channelFor(NotificationType.PASSWORD_RESET)).isSameAs(emailChannel);
    }

    @Test
    void theOtpGoesToTheSmsChannel() {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(emailChannel, smsChannel));

        assertThat(factory.channelFor(NotificationType.PHONE_OTP)).isSameAs(smsChannel);
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyNotificationTypeHasAChannel(NotificationType type) {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(emailChannel, smsChannel));

        assertThat(factory.channelFor(type).supports()).isEqualTo(type.getChannel());
    }

    @Test
    void aMissingChannelIsAConfigurationErrorNotASilentNoOp() {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(emailChannel));

        assertThatIllegalStateException()
                .isThrownBy(() -> factory.channelFor(NotificationType.PHONE_OTP))
                .withMessageContaining("SMS");
    }

    @Test
    void twoChannelsClaimingTheSameTransportFailFast() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new NotificationChannelFactory(
                        List.of(smsChannel, new StubChannel(ChannelType.SMS))))
                .withMessageContaining("SMS");
    }

    private record StubChannel(ChannelType type) implements NotificationChannel {

        @Override
        public ChannelType supports() {
            return type;
        }

        @Override
        public void send(Notification notification) {
            // Selection is what is under test here; delivery is tested per channel.
        }
    }
}
