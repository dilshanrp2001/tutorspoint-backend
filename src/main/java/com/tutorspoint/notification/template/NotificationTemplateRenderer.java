package com.tutorspoint.notification.template;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.notification.domain.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/**
 * Turns a {@link Notification} into the text that is actually delivered, in the
 * recipient's language.
 *
 * <p>Templates live at {@code templates/notifications/<language>/<key><extension>}.
 * A missing translation is not an error: the renderer logs it and falls back to
 * {@link Language#EN}, because a notification in the wrong language still beats no
 * verification email at all. Subjects are short enough to belong in the message
 * bundles rather than in a template of their own.
 */
@Slf4j
@Component
public class NotificationTemplateRenderer {

    /** Classpath root of the notification templates; also the Thymeleaf resolver prefix. */
    public static final String TEMPLATE_ROOT = "templates/notifications/";

    private static final Language FALLBACK_LANGUAGE = Language.EN;
    private static final String SUBJECT_KEY_FORMAT = "notification.%s.subject";

    private final ITemplateEngine templateEngine;
    private final MessageSource messageSource;

    public NotificationTemplateRenderer(@Qualifier("notificationTemplateEngine") ITemplateEngine templateEngine,
                                        MessageSource messageSource) {
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
    }

    /** Renders the notification body in the requested format. */
    public String render(Notification notification, TemplateFormat format) {
        String template = resolveTemplate(notification.getLanguage(), notification.templateKey(), format);
        Context context = new Context(localeOf(notification.getLanguage()), notification.getVariables());
        return templateEngine.process(template, context);
    }

    /** The translated subject line, for channels that have one. */
    public String subject(Notification notification) {
        String key = SUBJECT_KEY_FORMAT.formatted(notification.templateKey());
        return messageSource.getMessage(key, null, localeOf(notification.getLanguage()));
    }

    /**
     * The recipient's language if that translation exists on the classpath, English
     * otherwise. Checked here rather than left to the engine so the fallback is a
     * logged, deliberate decision instead of a swallowed resolution failure.
     */
    String resolveTemplate(Language language, String templateKey, TemplateFormat format) {
        String preferred = templateName(language, templateKey, format);
        if (new ClassPathResource(TEMPLATE_ROOT + preferred).exists()) {
            return preferred;
        }
        log.warn("No {} template for {} in {}; falling back to {}",
                format, templateKey, language, FALLBACK_LANGUAGE);
        return templateName(FALLBACK_LANGUAGE, templateKey, format);
    }

    private static String templateName(Language language, String key, TemplateFormat format) {
        return languageDirectory(language) + "/" + key + format.getExtension();
    }

    private static String languageDirectory(Language language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    private static Locale localeOf(Language language) {
        return Locale.of(languageDirectory(language));
    }
}
