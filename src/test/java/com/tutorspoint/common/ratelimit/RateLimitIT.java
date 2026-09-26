package com.tutorspoint.common.ratelimit;

import com.tutorspoint.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limits, switched on, through the whole stack: the filter's position in the security chain,
 * the body it reads and hands on, the 429 envelope and its {@code Retry-After}.
 *
 * <p>Its own small numbers in its own context, since the test profile turns limiting off. The
 * limiter keeps its buckets for the life of that context, so each test calls from its own client
 * address and names its own accounts - no test can spend another's allowance.
 */
@Transactional
@TestPropertySource(properties = {
        "tutorspoint.rate-limit.enabled=true",
        "tutorspoint.rate-limit.actions.login.per-client.requests=100",
        "tutorspoint.rate-limit.actions.login.per-account.requests=2",
        "tutorspoint.rate-limit.actions.login.per-account.per=1h",
        "tutorspoint.rate-limit.actions.search.per-client.requests=3",
        "tutorspoint.rate-limit.actions.search.per-client.per=1h",
        "tutorspoint.rate-limit.actions.password-reset.per-client.requests=1",
        "tutorspoint.rate-limit.actions.password-reset.per-client.per=1h"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("an account is limited however it is spelt, while the body still reaches sign-in intact")
    void signInIsLimitedPerAccount() throws Exception {
        // Unknown credentials, so each allowed attempt is an ordinary failed sign-in. That it is
        // not a 400 is the proof that the body the filter inspected was handed on whole.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(login("10.1.0.1", "limited@example.lk"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("10.1.0.2", "  LIMITED@example.lk "))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(RateLimitFilter.CODE));

        mockMvc.perform(login("10.1.0.2", "someone.else@example.lk"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("search is limited per client address, and another address is unaffected")
    void searchIsLimitedPerClient() throws Exception {
        for (int search = 0; search < 3; search++) {
            mockMvc.perform(search("10.2.0.1")).andExpect(status().isOk());
        }
        mockMvc.perform(search("10.2.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        mockMvc.perform(search("10.2.0.2")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a rate-limited response is still a normal response: request id and security headers included")
    void aLimitedResponseKeepsItsHeaders() throws Exception {
        mockMvc.perform(resetPassword("10.3.0.1")).andExpect(status().is4xxClientError());
        mockMvc.perform(resetPassword("10.3.0.1").secure(true))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("routes that are not limited are never counted")
    void otherRoutesAreUnlimited() throws Exception {
        for (int call = 0; call < 10; call++) {
            mockMvc.perform(get("/api/reference/mediums").with(from("10.4.0.1"))).andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder login(String address, String email) {
        return post("/api/auth/login")
                .with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Wrong-password-1\"}".formatted(email));
    }

    private MockHttpServletRequestBuilder search(String address) {
        return get("/api/search/tutors").with(from(address));
    }

    private MockHttpServletRequestBuilder resetPassword(String address) {
        return post("/api/auth/reset-password")
                .with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"not-a-real-token\",\"newPassword\":\"Colombo-2026-x\"}");
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
