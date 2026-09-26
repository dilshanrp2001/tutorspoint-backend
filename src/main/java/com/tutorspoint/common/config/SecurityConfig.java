package com.tutorspoint.common.config;

import com.tutorspoint.auth.security.JwtAuthenticationFilter;
import com.tutorspoint.auth.security.RestAccessDeniedHandler;
import com.tutorspoint.auth.security.RestAuthenticationEntryPoint;
import com.tutorspoint.common.logging.RequestIdFilter;
import com.tutorspoint.common.ratelimit.RateLimitFilter;
import com.tutorspoint.common.storage.MediaUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * The one place routes are made public or protected, and the one place the response headers
 * that protect a browser are set.
 *
 * <p>Two layers of authorization, deliberately:
 *
 * <ul>
 *   <li><strong>Route rules here</strong> — coarse, and the first thing a request meets. They
 *       decide what may be reached without a token at all.</li>
 *   <li><strong>{@code @PreAuthorize} on service methods</strong>, enabled by
 *       {@link EnableMethodSecurity} — fine-grained, and they travel with the service rather
 *       than with the URL, so a later caller that is not an HTTP request is still checked.</li>
 * </ul>
 *
 * <p>Every route is named. What is not named is denied to everyone, signed in or not, so a new
 * controller is unreachable until somebody decides here who may reach it - rather than quietly
 * open to any account. {@code EndpointAuthorizationIT} fails when a mapped endpoint has no rule.
 *
 * <p>Stateless: no session is created, the {@code Authorization} header carries the whole
 * identity, and CSRF protection is therefore off — there is no ambient credential for a
 * cross-site request to ride on, and a token in a header is not sent by the browser on its own.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Open to everyone. Registration and recovery have to be: the caller has no token yet, by
     * definition. They are protected by rate limits, attempt ceilings and responses that reveal
     * nothing, not by authentication.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            // Liveness probes for the container platform; details are never shown.
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * Any signed-in account. Which role may do what inside each is decided by the
     * {@code @PreAuthorize} rule on the service method, and which rows it may touch by the
     * service's query - never by this list.
     */
    private static final String[] SIGNED_IN_PATHS = {
            "/api/account", "/api/account/**",
            "/api/tutors/me/**",
            "/api/documents/**",
            "/api/enquiries", "/api/enquiries/**",
            "/api/shortlist", "/api/shortlist/**"
    };

    /**
     * For API responses: nothing may load, nothing may frame them, and nothing may be submitted
     * from them. JSON needs none of it, and a response that is somehow rendered as a page - an
     * uploaded file opened directly, an error echoed back - then cannot run anything.
     *
     * <p>This is the API's policy, not the single-page app's. The SPA's HTML is served by the
     * reverse proxy (Phase 5.4), and its policy - {@code script-src 'self'}, {@code connect-src}
     * limited to this API's origin - belongs on that response. This one is compatible with it:
     * the SPA's {@code fetch} and {@code <img>} requests are governed by the page's policy, not
     * by the headers on what they fetch.
     */
    private static final String API_CSP = "default-src 'none'; img-src 'self'; media-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /**
     * Swagger UI is a page, and a strict API policy blocks it outright. Its own scripts and
     * styles, the inline styles it sets from script, and calls back to this origin. Only in the
     * profiles where it is served at all (application-prod.yml turns it off).
     */
    private static final String DOCS_CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    private static final long HSTS_MAX_AGE_SECONDS = Duration.ofDays(365).toSeconds();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher docsUi = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Sent on HTTPS responses only, which is what the header's own rules require.
                        // Behind the Phase 5.4 proxy a request is only known to be HTTPS once
                        // server.forward-headers-strategy trusts that proxy.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(docsUi),
                                new StaticHeadersWriter(CONTENT_SECURITY_POLICY, API_CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                docsUi,
                                new StaticHeadersWriter(CONTENT_SECURITY_POLICY, DOCS_CSP))))
                .authorizeHttpRequests(requests -> requests
                        // Browsers must be able to preflight a cross-origin call before they
                        // are willing to send the Authorization header on the real one.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Reference data is the vocabulary of the search form, and search is
                        // open to guests. Readable without a token; writable by nobody.
                        .requestMatchers(HttpMethod.GET, "/api/reference", "/api/reference/**").permitAll()
                        // A published tutor profile is the product: a parent compares tutors
                        // before deciding whether to register at all (FR-S4). One path segment
                        // only, so the tutor's own /api/tutors/me/profile routes are not
                        // matched here and stay behind the token.
                        .requestMatchers(HttpMethod.GET, "/api/tutors/*").permitAll()
                        // Search, for the same reason: looking costs nothing and needs no
                        // account. What it can return is limited by the query, not the caller.
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        // Profile photographs and intro videos, which are part of a public
                        // profile and therefore public themselves. The endpoint behind this
                        // serves only the public storage areas: a qualification document is
                        // reachable solely through /api/documents/{id}, which authorises every
                        // read and is deliberately not matched here.
                        .requestMatchers(HttpMethod.GET, MediaUrls.BASE_PATH + "**").permitAll()
                        // Moderation, verification and metrics. The admin services repeat this
                        // with @PreAuthorize, so a caller that is not a request is checked too.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(SIGNED_IN_PATHS).authenticated()
                        .anyRequest().denyAll())
                // Errors inside the chain still return the standard envelope: a client should
                // not have to parse two error shapes depending on how far its request got.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // After the token is read, so a signed-in caller is limited as an account too.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * The frontend's origin, and only that. No credentials mode: the token travels in a header
     * the SPA sets itself, never in a cookie, so there is nothing for {@code allowCredentials}
     * to permit.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(corsProperties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_LANGUAGE, RequestIdFilter.HEADER));
        // Readable by the SPA: how long to back off after a 429, and the id to quote in a report.
        cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, RequestIdFilter.HEADER));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** BCrypt, per NFR-4. Also hashes the phone OTP, which needs a salted, slow digest. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
