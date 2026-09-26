package com.tutorspoint.notification.sms;

/**
 * Our own SMS gateway contract. Every vendor is reached through an implementation of
 * this and nothing else: no vendor type appears in a service signature, a DTO or an
 * entity, so changing gateway is adding a class, not editing tested code.
 */
public interface SmsProvider {

    /**
     * @param phoneNumber recipient in E.164 form, for example {@code +94771234567};
     *                    an adapter reshapes it to whatever its gateway demands
     * @param message     the plain-text body, already rendered and translated
     * @throws com.tutorspoint.notification.NotificationDeliveryException if the gateway
     *         refuses or cannot be reached
     */
    void send(String phoneNumber, String message);
}
