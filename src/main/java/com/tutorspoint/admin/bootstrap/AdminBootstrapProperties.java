package com.tutorspoint.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first administrator's credentials, from the environment ({@code ADMIN_BOOTSTRAP_*}).
 *
 * <p>All optional: with no email set, bootstrapping is off, which is the normal state of every
 * environment after its first start. The values are never committed and never logged.
 */
@ConfigurationProperties(prefix = "tutorspoint.admin.bootstrap")
public record AdminBootstrapProperties(
        String email,
        String password,
        String fullName,
        String phoneNumber) {

    public boolean enabled() {
        return email != null && !email.isBlank();
    }
}
