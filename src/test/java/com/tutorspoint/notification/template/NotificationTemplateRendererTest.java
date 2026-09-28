package com.tutorspoint.notification.template;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.notification.config.NotificationConfig;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real templates and the real message bundles — mocking Thymeleaf
 * here would prove nothing, and a missing or mis-encoded Sinhala template is exactly
 * the sort of thing this must catch before a user does.
 */
class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer =
            new NotificationTemplateRenderer(new NotificationConfig().notificationTemplateEngine(), messageSource());

    private static MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Notification otp(Language language) {
        return Notification.builder()
                .type(NotificationType.PHONE_OTP)
                .recipient("+94771234567")
                .language(language)
                .variable("code", "123456")
                .variable("expiryMinutes", 5)
                .build();
    }

    private static Notification emailVerification(Language language) {
        return Notification.builder()
                .type(NotificationType.EMAIL_VERIFICATION)
                .recipient("nimal@example.com")
                .language(language)
                .variable("fullName", "Nimal Perera")
                .variable("verificationUrl", "https://tutorspoint.xyz/verify?token=abc")
                .variable("expiryHours", 24)
                .build();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theOtpRendersInEveryLanguageWithTheCodeInterpolated(Language language) {
        String body = renderer.render(otp(language), TemplateFormat.TEXT).strip();

        assertThat(body).contains("123456").contains("5").doesNotContain("${");
        assertThat(body).hasSizeLessThan(160);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theVerificationEmailRendersInEveryLanguageWithItsLinkAndName(Language language) {
        String body = renderer.render(emailVerification(language), TemplateFormat.HTML);

        assertThat(body)
                .contains("Nimal Perera")
                .contains("https://tutorspoint.xyz/verify?token=abc")
                .contains("24")
                .doesNotContain("th:text");
    }

    @Test
    void sinhalaAndTamilBodiesAreGenuinelyDifferentText() {
        String sinhala = renderer.render(otp(Language.SI), TemplateFormat.TEXT).strip();
        String tamil = renderer.render(otp(Language.TA), TemplateFormat.TEXT).strip();
        String english = renderer.render(otp(Language.EN), TemplateFormat.TEXT).strip();

        assertThat(sinhala).isNotEqualTo(english).isNotEqualTo(tamil);
        assertThat(tamil).isNotEqualTo(english);
    }

    @Test
    void aTranslatedTemplateIsPreferred() {
        assertThat(renderer.resolveTemplate(Language.SI, "otp", TemplateFormat.TEXT))
                .isEqualTo("si/otp.txt");
    }

    @Test
    void anUntranslatedTemplateFallsBackToEnglishRatherThanFailing() {
        assertThat(renderer.resolveTemplate(Language.TA, "not-yet-translated", TemplateFormat.HTML))
                .isEqualTo("en/not-yet-translated.html");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyEmailSubjectIsTranslated(Language language) {
        assertThat(renderer.subject(emailVerification(language))).isNotBlank();
        assertThat(renderer.subject(passwordReset(language))).isNotBlank();
    }

    @Test
    void subjectsDifferPerLanguage() {
        assertThat(renderer.subject(emailVerification(Language.SI)))
                .isNotEqualTo(renderer.subject(emailVerification(Language.EN)))
                .isNotEqualTo(renderer.subject(emailVerification(Language.TA)));
    }

    private static Notification passwordReset(Language language) {
        return Notification.builder()
                .type(NotificationType.PASSWORD_RESET)
                .recipient("nimal@example.com")
                .language(language)
                .variable("fullName", "Nimal Perera")
                .variable("resetUrl", "https://tutorspoint.xyz/reset?token=abc")
                .variable("expiryMinutes", 30)
                .build();
    }
}
