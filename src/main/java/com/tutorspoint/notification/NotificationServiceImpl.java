package com.tutorspoint.notification;

import com.tutorspoint.notification.channel.NotificationChannelFactory;
import com.tutorspoint.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Chooses the channel and delivers, off the caller's thread and without ever throwing
 * back at it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationChannelFactory channelFactory;

    /**
     * Catches {@link Exception} deliberately. This is the outermost frame of a
     * fire-and-forget task: anything escaping here is lost on a pool thread, and
     * nothing a channel can fail at justifies losing the account the caller just
     * created. The failure is logged with its stack trace so it stays diagnosable.
     */
    @Async
    @Override
    public void send(Notification notification) {
        try {
            channelFactory.channelFor(notification.getType()).send(notification);
            log.info("Delivered {} to {} over {}",
                    notification.getType(), notification.maskedRecipient(), notification.channelType());
        } catch (Exception e) {
            log.error("Failed to deliver {} to {} over {}",
                    notification.getType(), notification.maskedRecipient(), notification.channelType(), e);
        }
    }
}
