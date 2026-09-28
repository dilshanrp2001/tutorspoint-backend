package com.tutorspoint.notification.channel;

import com.tutorspoint.notification.domain.ChannelType;
import com.tutorspoint.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the channel a notification type travels on (Factory).
 *
 * <p>Channels are discovered, not listed: Spring injects every
 * {@link NotificationChannel} bean and each declares the type it answers for, so a new
 * transport is a new {@code @Component} and this class is never edited again (OCP).
 * Two channels claiming the same type is a wiring mistake and fails at startup rather
 * than silently letting the last one win.
 */
@Component
public class NotificationChannelFactory {

    private final Map<ChannelType, NotificationChannel> channelsByType;

    public NotificationChannelFactory(List<NotificationChannel> channels) {
        Map<ChannelType, NotificationChannel> byType = new EnumMap<>(ChannelType.class);
        for (NotificationChannel channel : channels) {
            NotificationChannel clash = byType.put(channel.supports(), channel);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two NotificationChannel beans claim %s: %s and %s".formatted(
                                channel.supports(),
                                clash.getClass().getName(),
                                channel.getClass().getName()));
            }
        }
        this.channelsByType = Map.copyOf(byType);
    }

    /**
     * @throws IllegalStateException if no channel serves the type's transport, which
     *         means the application is misconfigured, not that the caller erred
     */
    public NotificationChannel channelFor(NotificationType type) {
        NotificationChannel channel = channelsByType.get(type.getChannel());
        if (channel == null) {
            throw new IllegalStateException(
                    "No NotificationChannel is registered for %s, required by %s"
                            .formatted(type.getChannel(), type));
        }
        return channel;
    }
}
