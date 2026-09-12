package com.tutorspoint.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.notification.NotificationService;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The journey end to end, over HTTP, against a real PostgreSQL and through the real security
 * filter chain: register, confirm both channels, sign in, reach a protected endpoint, refresh,
 * sign out.
 *
 * <p>{@link NotificationService} is the one thing replaced by a mock — not to avoid sending
 * mail, but because the verification token and the OTP exist nowhere else. Only digests are
 * stored, so capturing the outgoing message is the only way a test can learn the secrets, and
 * it is also how a test proves the right secret went to the right address.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String EMAIL = "flow.tutor@example.lk";
    private static final String PHONE = "+94771000001";
    private static final String PASSWORD = "Colombo2026";

    @MockitoBean
    private NotificationService notifications;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("register -> verify email -> verify phone -> login -> protected endpoint -> refresh -> logout")
    void theWholeJourney() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "TUTOR",
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "Nimali Perera",
                                  "phoneNumber": "%s",
                                  "preferredLanguage": "SI"
                                }
                                """.formatted(EMAIL, PASSWORD, PHONE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.phoneVerified").value(false))
                // Nothing internal leaves the server, whatever the entity holds.
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        List<Notification> sent = capturedNotifications();
        Notification verificationEmail = notificationOfType(sent, NotificationType.EMAIL_VERIFICATION);
        Notification otpSms = notificationOfType(sent, NotificationType.PHONE_OTP);
        assertThat(verificationEmail.getRecipient()).isEqualTo(EMAIL);
        assertThat(otpSms.getRecipient()).isEqualTo(PHONE);

        // A pending account cannot sign in, and is told why rather than being told its
        // password is wrong.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(EMAIL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(tokenFrom(verificationEmail, "verificationUrl"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"code\": \"%s\"}"
                                .formatted(EMAIL, otpSms.getVariables().get("code"))))
                .andExpect(status().isOk());

        JsonNode tokens = body(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn());
        String accessToken = tokens.at("/data/accessToken").asText();
        String refreshToken = tokens.at("/data/refreshToken").asText();
        assertThat(tokens.at("/data/expiresInSeconds").asLong()).isPositive();

        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.lastLoginAt").isNotEmpty());

        JsonNode rotated = body(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn());
        String rotatedRefreshToken = rotated.at("/data/refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        // The rotated-out token is dead, which is what makes a leaked one usable at most once.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rotatedRefreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("forgot-password answers the same for a stranger as for a member")
    void forgotPasswordRevealsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody.at.all@example.lk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());

        then(notifications).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("a rejected request body is explained field by field")
    void validationIsRejectedAtTheEdge() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "TUTOR",
                                  "email": "not-an-email",
                                  "password": "short",
                                  "fullName": "",
                                  "phoneNumber": "0771234567",
                                  "preferredLanguage": "SI"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.error.fieldErrors.password").isNotEmpty())
                .andExpect(jsonPath("$.error.fieldErrors.fullName").isNotEmpty())
                // A local number without a country code: the SMS gateway is addressed in E.164.
                .andExpect(jsonPath("$.error.fieldErrors.phoneNumber").isNotEmpty());

        then(notifications).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ADMIN cannot be self-registered")
    void theAdminRoleIsNotOnOffer() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN",
                                  "email": "wannabe.admin@example.lk",
                                  "password": "Colombo2026",
                                  "fullName": "Wannabe Admin",
                                  "phoneNumber": "+94771000099",
                                  "preferredLanguage": "EN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));

        then(notifications).shouldHaveNoInteractions();
    }

    private List<Notification> capturedNotifications() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notifications).should(atLeast(1)).send(captor.capture());
        return captor.getAllValues();
    }

    private static Notification notificationOfType(List<Notification> sent, NotificationType type) {
        return sent.stream()
                .filter(notification -> notification.getType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " notification was sent"));
    }

    /** The token as the user would get it: out of the link, not out of the database. */
    private static String tokenFrom(Notification notification, String urlVariable) {
        String url = (String) notification.getVariables().get(urlVariable);
        String encoded = url.substring(url.indexOf("?token=") + "?token=".length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
