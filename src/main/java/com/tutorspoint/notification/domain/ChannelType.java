package com.tutorspoint.notification.domain;

/**
 * The transports this module can deliver over. A channel type is answered by exactly
 * one {@code NotificationChannel} implementation; adding a transport means adding a
 * constant and a class, never editing the ones already tested (OCP).
 */
public enum ChannelType {

    /** Templated HTML email over SMTP. */
    EMAIL,

    /** Plain-text SMS through the configured gateway. Costs money per message. */
    SMS
}
