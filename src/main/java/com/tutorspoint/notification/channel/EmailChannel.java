package com.tutorspoint.notification.channel;

import com.tutorspoint.notification.NotificationDeliveryException;
import com.tutorspoint.notification.config.NotificationEmailProperties;
import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.template.NotificationTemplateRenderer;
import com.tutorspoint.notification.template.TemplateFormat;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Delivers templated HTML email over SMTP. The only class in the application that is
 * allowed to know what a {@code MimeMessage} is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    /** The Content-ID every HTML template points its header image at. */
    static final String LOGO_CONTENT_ID = "tutorspoint-logo";

    private static final Resource LOGO = new ClassPathResource("mail/logo.png");

    private final JavaMailSender mailSender;
    private final NotificationTemplateRenderer renderer;
    private final NotificationEmailProperties properties;

    @Override
    public ChannelType supports() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            // Multipart so the logo travels inside the message: Gmail and Outlook block
            // remote images by default and do not render SVG at all.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(notification.getRecipient());
            helper.setSubject(renderer.subject(notification));
            helper.setText(renderer.render(notification, TemplateFormat.HTML), true);
            // Must follow setText: the templates reference it as cid:tutorspoint-logo.
            helper.addInline(LOGO_CONTENT_ID, LOGO, "image/png");
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new NotificationDeliveryException(
                    "Could not compose %s email for %s"
                            .formatted(notification.getType(), notification.maskedRecipient()), e);
        }

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new NotificationDeliveryException(
                    "SMTP rejected the %s email for %s"
                            .formatted(notification.getType(), notification.maskedRecipient()), e);
        }
        log.debug("Sent {} email to {}", notification.getType(), notification.maskedRecipient());
    }
}
