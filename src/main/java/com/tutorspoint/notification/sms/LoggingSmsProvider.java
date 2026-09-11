package com.tutorspoint.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The dev-profile gateway: writes the message to the log instead of sending it. SMS is
 * billed per message, so local development and demos must never reach a real gateway —
 * and printing the OTP is how a developer completes a signup without a phone.
 */
@Slf4j
@Component
@Profile("dev")
public class LoggingSmsProvider implements SmsProvider {

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[dev SMS] to {} :: {}", phoneNumber, message);
    }
}
