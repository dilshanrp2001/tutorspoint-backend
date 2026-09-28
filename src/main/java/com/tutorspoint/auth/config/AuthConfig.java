package com.tutorspoint.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds and validates the auth module's configuration at startup. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class AuthConfig {
}
