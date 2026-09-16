package com.tutorspoint.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The dev-profile gateway: writes the message to the log instead of sending it. SMS is
 * billed per message, so local development and demos must never reach a real gateway —
 * and printing the OTP is how a developer completes a signup without a phone.
 *
 * <p>The one deliberate exception to "never log an OTP": this class stands in for the phone,
 * and only in the dev profile, where there is no real account to protect. The number is still
 * masked, because a developer's database can hold real people's numbers.
 */
@Slf4j
@Component
@Profile("dev")
public class LoggingSmsProvider implements SmsProvider {

    private static final int VISIBLE_DIGITS = 3;

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[dev SMS] to {} :: {}", mask(phoneNumber), message);
    }

    private static String mask(String phoneNumber) {
        int keep = Math.min(VISIBLE_DIGITS, phoneNumber.length());
        return "***" + phoneNumber.substring(phoneNumber.length() - keep);
    }
}
