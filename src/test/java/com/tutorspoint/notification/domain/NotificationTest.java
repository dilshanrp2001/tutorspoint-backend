package com.tutorspoint.notification.domain;

import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The builder's guarantees: no half-built message, and no secret in a log line. */
class NotificationTest {

    @Test
    void channelAndTemplateComeFromTheTypeRatherThanTheCaller() {
        Notification notification = Notification.builder()
                .type(NotificationType.PHONE_OTP)
                .recipient("+94771234567")
                .language(Language.TA)
                .variable("code", "123456")
                .build();

        assertThat(notification.channelType()).isEqualTo(ChannelType.SMS);
        assertThat(notification.templateKey()).isEqualTo("otp");
        assertThat(notification.getVariables()).containsEntry("code", "123456");
    }

    @Test
    void anUnspecifiedLanguageFallsBackToEnglish() {
        Notification notification = Notification.builder()
                .type(NotificationType.EMAIL_VERIFICATION)
                .recipient("nimal@example.com")
                .build();

        assertThat(notification.getLanguage()).isEqualTo(Language.EN);
    }

    @Test
    void aTypelessOrRecipientlessNotificationCannotBeBuilt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Notification.builder().recipient("nimal@example.com").build())
                .withMessageContaining("type");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Notification.builder()
                        .type(NotificationType.EMAIL_VERIFICATION)
                        .recipient("   ")
                        .build())
                .withMessageContaining("recipient");
    }

    @Test
    void variablesAreImmutableOnceBuilt() {
        Notification notification = Notification.builder()
                .type(NotificationType.PHONE_OTP)
                .recipient("+94771234567")
                .variable("code", "123456")
                .build();

        assertThat(notification.getVariables()).isUnmodifiable();
    }

    @Test
    void emailAddressesAreMaskedForLogging() {
        Notification notification = Notification.builder()
                .type(NotificationType.EMAIL_VERIFICATION)
                .recipient("nimal.perera@example.com")
                .build();

        assertThat(notification.maskedRecipient()).isEqualTo("n***@example.com");
    }

    @Test
    void phoneNumbersKeepOnlyTheirLastThreeDigits() {
        Notification notification = Notification.builder()
                .type(NotificationType.PHONE_OTP)
                .recipient("+94771234567")
                .build();

        assertThat(notification.maskedRecipient()).isEqualTo("***567");
    }

    @Test
    void toStringNeverCarriesTheRecipientOrTheCode() {
        Notification notification = Notification.builder()
                .type(NotificationType.PHONE_OTP)
                .recipient("+94771234567")
                .variable("code", "123456")
                .build();

        assertThat(notification).hasToString(
                "Notification[type=PHONE_OTP, recipient=***567, language=EN]");
    }
}
