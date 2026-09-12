package com.tutorspoint.common.config;

import com.tutorspoint.auth.security.JwtAuthenticationFilter;
import com.tutorspoint.auth.security.RestAccessDeniedHandler;
import com.tutorspoint.auth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The one place routes are made public or protected.
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
 * <p>Stateless: no session is created, the {@code Authorization} header carries the whole
 * identity, and CSRF protection is therefore off — there is no ambient credential for a
 * cross-site request to ride on, and a token in a header is not sent by the browser on its own.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Open to everyone. Registration and recovery have to be: the caller has no token yet, by
     * definition. They are protected by rate caps, attempt ceilings and responses that reveal
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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                        .anyRequest().authenticated())
                // Errors inside the chain still return the standard envelope: a client should
                // not have to parse two error shapes depending on how far its request got.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    /** BCrypt, per NFR-4. Also hashes the phone OTP, which needs a salted, slow digest. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
