package com.tutorspoint.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.notification.sms.SmsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nothing secret reaches the log: no password, no token, no OTP, no full phone number.
 *
 * <p>The auth journey is run with the application's loggers at DEBUG and everything written to
 * the console captured - including what the notification pipeline logs on its pool threads when
 * the SMS gateway is unreachable, which is the failure path where a gateway URL full of
 * credentials and message text would surface. The test profile points the real Notify.lk adapter
 * at a dead port, so that failure really happens here.
 *
 * <p>The SMS adapter is spied, not replaced: the real one still runs and fails, and the spy is only
 * how this test learns the OTP it then looks for.
 *
 * <p>Not transactional: the notification runs after commit on another thread, and it is that
 * thread's log lines this is most interested in. The accounts use addresses no other test does.
 */
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLoggingIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Kandy-Lake-2026";
    private static final String NEW_PARENT_EMAIL = "logging.new@example.lk";
    private static final String NEW_PARENT_PHONE = "+94775934521";
    private static final String ACTIVE_PARENT_EMAIL = "logging.active@example.lk";
    private static final String ACTIVE_PARENT_PHONE = "+94775934522";
    private static final Pattern OTP = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @MockitoSpyBean
    private SmsProvider smsProvider;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @BeforeEach
    void logEverything() {
        loggingSystem.setLogLevel("com.tutorspoint", LogLevel.DEBUG);
    }

    @AfterEach
    void restoreLogging() {
        loggingSystem.setLogLevel("com.tutorspoint", LogLevel.INFO);
    }

    @Test
    @DisplayName("registration, OTP, sign-in, refresh and recovery log no password, token, OTP or phone number")
    void theAuthJourneyLogsNothingSecret(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"role": "PARENT", "email": "%s", "password": "%s", "fullName": "Log Test",
                         "phoneNumber": "%s", "preferredLanguage": "EN"}
                        """.formatted(NEW_PARENT_EMAIL, PASSWORD, NEW_PARENT_PHONE)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> smsText = ArgumentCaptor.forClass(String.class);
        verify(smsProvider, timeout(10_000)).send(eq(NEW_PARENT_PHONE), smsText.capture());
        String otp = otpIn(smsText.getValue());
        awaitLogLine(output, "Failed to deliver PHONE_OTP");

        mockMvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"code\": \"%s\"}".formatted(NEW_PARENT_EMAIL, wrong(otp))));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(NEW_PARENT_EMAIL, PASSWORD)));

        Parent active = new Parent(ACTIVE_PARENT_EMAIL, passwordEncoder.encode(PASSWORD), "Active Log Test",
                ACTIVE_PARENT_PHONE, Language.EN);
        active.verifyEmail();
        active.verifyPhone();
        active.activate();
        users.saveAndFlush(active);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"not-%s\"}".formatted(ACTIVE_PARENT_EMAIL, PASSWORD)));
        JsonNode tokens = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(ACTIVE_PARENT_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String accessToken = tokens.at("/data/accessToken").asText();
        String refreshToken = tokens.at("/data/refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
        mockMvc.perform(post("/api/enquiries").header("Authorization", "Bearer " + accessToken + "tampered")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(ACTIVE_PARENT_EMAIL)));
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"reset-token-that-is-not-real\", \"newPassword\": \"%s-new\"}".formatted(PASSWORD)));
        awaitLogLine(output, "Failed to deliver PASSWORD_RESET");

        String log = output.getAll();
        assertThat(log).as("the captured log is the journey's").contains("Registered account");
        assertThat(log)
                .doesNotContain(PASSWORD)
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken)
                .doesNotContain("reset-token-that-is-not-real")
                .doesNotContain(NEW_PARENT_PHONE.substring(1), ACTIVE_PARENT_PHONE.substring(1))
                .doesNotContain(NEW_PARENT_PHONE.substring(3), ACTIVE_PARENT_PHONE.substring(3));
        assertThat(log).as("the OTP").doesNotContainPattern("(?<!\\d)" + otp + "(?!\\d)");
    }

    private static String otpIn(String message) {
        Matcher matcher = OTP.matcher(message);
        assertThat(matcher.find()).as("an OTP in the SMS text").isTrue();
        return matcher.group(1);
    }

    private static String wrong(String otp) {
        return otp.equals("000000") ? "111111" : "000000";
    }

    /** The notification pipeline is asynchronous; its log lines arrive after the response. */
    private static void awaitLogLine(CapturedOutput output, String line) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!output.getAll().contains(line) && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(line);
    }
}
