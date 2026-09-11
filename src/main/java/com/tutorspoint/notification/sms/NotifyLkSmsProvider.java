package com.tutorspoint.notification.sms;

import com.tutorspoint.notification.NotificationDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter for the Notify.lk gateway (Adapter).
 *
 * <p>Everything awkward about that API is contained here and nowhere else: it is a
 * GET-shaped POST whose credentials and message body all travel as query parameters, it
 * wants the number without its leading {@code +}, and it answers {@code 200 OK} with a
 * body of {@code {"status":"error"}} when a send fails. Callers see none of that — they
 * see {@link SmsProvider#send}, which either returns or throws.
 *
 * <p>Active outside the dev profile, where {@link LoggingSmsProvider} stands in.
 */
@Slf4j
@Component
@Profile("!dev")
@EnableConfigurationProperties(NotifyLkProperties.class)
public class NotifyLkSmsProvider implements SmsProvider {

    private static final String SEND_PATH = "/send";
    private static final String STATUS_SUCCESS = "success";
    private static final String NON_DIGITS = "[^0-9]";

    private final RestClient restClient;
    private final NotifyLkProperties properties;

    public NotifyLkSmsProvider(RestClient.Builder restClientBuilder, NotifyLkProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public void send(String phoneNumber, String message) {
        SendResponse response;
        try {
            response = restClient.post()
                    .uri(uri -> uri.path(SEND_PATH)
                            .queryParam("user_id", properties.userId())
                            .queryParam("api_key", properties.apiKey())
                            .queryParam("sender_id", properties.senderId())
                            .queryParam("to", toGatewayNumber(phoneNumber))
                            .queryParam("message", message)
                            .build())
                    .retrieve()
                    .body(SendResponse.class);
        } catch (RestClientException e) {
            throw new NotificationDeliveryException("Notify.lk could not be reached", e);
        }

        if (response == null || !STATUS_SUCCESS.equalsIgnoreCase(response.status())) {
            throw new NotificationDeliveryException(
                    "Notify.lk refused the message: %s".formatted(describe(response)));
        }
    }

    /** E.164 in, bare international digits out: {@code +94771234567} to {@code 94771234567}. */
    private static String toGatewayNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll(NON_DIGITS, "");
        if (digits.isEmpty()) {
            throw new NotificationDeliveryException(
                    "Phone number carries no digits and cannot be sent to Notify.lk");
        }
        return digits;
    }

    private static String describe(SendResponse response) {
        if (response == null) {
            return "empty response body";
        }
        return "%s / %s".formatted(response.status(), response.message());
    }

    /** The slice of the gateway's JSON we act on. Never leaves this class. */
    private record SendResponse(String status, String message) {
    }
}
