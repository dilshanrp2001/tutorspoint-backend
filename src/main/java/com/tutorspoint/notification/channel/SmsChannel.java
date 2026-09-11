package com.tutorspoint.notification.channel;

import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.sms.SmsProvider;
import com.tutorspoint.notification.template.NotificationTemplateRenderer;
import com.tutorspoint.notification.template.TemplateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Renders the SMS body and hands it to whichever {@link SmsProvider} the active profile
 * supplies. It holds no gateway knowledge of its own — that is the adapter's job — so
 * swapping vendors leaves this class untouched (DIP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannel implements NotificationChannel {

    private final SmsProvider smsProvider;
    private final NotificationTemplateRenderer renderer;

    @Override
    public ChannelType supports() {
        return ChannelType.SMS;
    }

    @Override
    public void send(Notification notification) {
        // Text templates end in a newline that would otherwise be billed as content.
        String body = renderer.render(notification, TemplateFormat.TEXT).strip();
        smsProvider.send(notification.getRecipient(), body);
        log.debug("Sent {} SMS to {}", notification.getType(), notification.maskedRecipient());
    }
}
