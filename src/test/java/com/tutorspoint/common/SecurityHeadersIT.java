package com.tutorspoint.common;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.common.logging.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The headers a browser relies on, the CORS policy, and the request id - on real responses from
 * the real filter chain, public and protected alike.
 */
class SecurityHeadersIT extends AbstractIntegrationTest {

    /** application.yml's default, which the test profile does not override. */
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Test
    @DisplayName("an HTTPS API response carries HSTS, nosniff, DENY framing, no-referrer and the strict API policy")
    void apiResponsesCarryTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/reference/mediums").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("an error response from inside the security chain is protected the same way")
    void rejectedRequestsCarryTheHeadersToo() throws Exception {
        mockMvc.perform(get("/api/account").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    @Test
    @DisplayName("HSTS is not sent over plain HTTP, where a browser would ignore it anyway")
    void noHstsOverHttp() throws Exception {
        mockMvc.perform(get("/api/reference/mediums"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("Swagger UI gets a policy that lets its own scripts run, and still cannot be framed")
    void docsUiHasItsOwnPolicy() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("a preflight from the frontend origin is allowed, with the headers the SPA needs")
    void preflightFromTheFrontendIsAllowed() throws Exception {
        mockMvc.perform(options("/api/enquiries")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("any other origin is refused, and is never echoed back or answered with a wildcard")
    void preflightFromAnotherOriginIsRefused() throws Exception {
        mockMvc.perform(options("/api/enquiries")
                        .header(HttpHeaders.ORIGIN, "https://tutorspoint.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get("/api/reference/mediums").header(HttpHeaders.ORIGIN, "https://tutorspoint.example.com"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("the SPA can read Retry-After and the request id on a cross-origin response")
    void rateLimitAndRequestIdHeadersAreExposed() throws Exception {
        mockMvc.perform(get("/api/reference/mediums").header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(RequestIdFilter.HEADER)));
    }

    @Test
    @DisplayName("every response has a request id; a well-formed one from the caller is kept, anything else replaced")
    void everyResponseHasARequestId() throws Exception {
        MvcResult generated = mockMvc.perform(get("/api/account")).andReturn();
        assertThat(generated.getResponse().getHeader(RequestIdFilter.HEADER))
                .as("on a request the security chain rejected")
                .matches("[0-9a-f-]{36}");

        mockMvc.perform(get("/api/reference/mediums").header(RequestIdFilter.HEADER, "edge-7f3a9c21"))
                .andExpect(header().string(RequestIdFilter.HEADER, "edge-7f3a9c21"));

        String forged = "abc\n{\"level\":\"ERROR\"}";
        String replaced = mockMvc.perform(get("/api/reference/mediums").header(RequestIdFilter.HEADER, forged))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(replaced).isNotEqualTo(forged).matches("[0-9a-f-]{36}");
    }
}
