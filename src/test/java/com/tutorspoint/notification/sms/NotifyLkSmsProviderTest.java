package com.tutorspoint.notification.sms;

import com.tutorspoint.notification.NotificationDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * The adapter against a stubbed gateway. What matters is that Notify.lk's quirks stop
 * here: the reshaped number, the credentials in the query string, and a body that says
 * "error" inside an HTTP 200.
 */
class NotifyLkSmsProviderTest {

    private static final NotifyLkProperties PROPERTIES = new NotifyLkProperties(
            "https://app.notify.lk/api/v1", "user-1", "key-1", "TutorsPoint");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private NotifyLkSmsProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new NotifyLkSmsProvider(builder, PROPERTIES);
    }

    @Test
    void itStripsThePlusAndSendsTheCredentialsTheGatewayExpects() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://app.notify.lk/api/v1/send")))
                .andExpect(method(POST))
                .andExpect(queryParam("user_id", "user-1"))
                .andExpect(queryParam("api_key", "key-1"))
                .andExpect(queryParam("sender_id", "TutorsPoint"))
                .andExpect(queryParam("to", "94771234567"))
                // The message body travels in the query string, percent-encoded — one of
                // the vendor quirks this adapter exists to keep out of the rest of the code.
                .andExpect(queryParam("message", "Your%20code%20is%20123456"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        provider.send("+94771234567", "Your code is 123456");

        server.verify();
    }

    @Test
    void anErrorBodyInsideAnHttp200IsStillAFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://app.notify.lk/api/v1/send")))
                .andRespond(withSuccess("{\"status\":\"error\",\"message\":\"invalid number\"}",
                        MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> provider.send("+94771234567", "Your code is 123456"))
                .withMessageContaining("invalid number");
    }

    @Test
    void anUnreachableGatewayIsReportedAsADeliveryFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://app.notify.lk/api/v1/send")))
                .andRespond(withServerError());

        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> provider.send("+94771234567", "Your code is 123456"))
                .withMessageContaining("could not be reached");
    }

    @Test
    void aTransportFailureCarriesNoCredentialNumberOrMessageText() {
        // A ResourceAccessException names the URL it failed on, and this URL holds the API key,
        // the recipient and the OTP. The caller logs the exception it gets, stack and causes.
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://app.notify.lk/api/v1/send")))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("Read timed out");
                });

        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> provider.send("+94771234567", "Your code is 123456"))
                .satisfies(e -> {
                    StringBuilder everything = new StringBuilder();
                    for (Throwable t = e; t != null; t = t.getCause()) {
                        everything.append(t).append('\n');
                    }
                    org.assertj.core.api.Assertions.assertThat(everything.toString())
                            .contains("SocketTimeoutException")
                            .doesNotContain("key-1", "94771234567", "123456");
                });
    }

    @Test
    void aNumberWithNoDigitsNeverReachesTheGateway() {
        assertThatExceptionOfType(NotificationDeliveryException.class)
                .isThrownBy(() -> provider.send("+", "Your code is 123456"));

        server.verify();
    }
}
