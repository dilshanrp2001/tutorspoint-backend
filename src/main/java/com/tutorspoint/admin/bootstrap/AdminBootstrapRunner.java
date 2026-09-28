package com.tutorspoint.admin.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator at startup when {@code ADMIN_BOOTSTRAP_EMAIL} is set.
 *
 * <p>Every profile, production included - that is where the first admin is needed. Idempotent:
 * once the account exists a restart changes nothing, and the variables should then be removed
 * from the environment so the password is not left lying in it.
 *
 * <p>A missing or too-short password stops startup. A misconfigured bootstrap that silently did
 * nothing would leave a fresh deployment with nobody able to administer it, discovered only when
 * somebody tries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapRunner implements CommandLineRunner {

    private final AdminBootstrapProperties properties;
    private final AdminProvisioningService provisioning;

    @Override
    public void run(String... args) {
        if (!properties.enabled()) {
            return;
        }
        var outcome = provisioning.provision(properties.email(), properties.password(),
                properties.fullName(), properties.phoneNumber());
        // The email identifies which account; the password is never logged, in any branch.
        switch (outcome) {
            case CREATED -> log.info("Bootstrap administrator {} created. Remove the ADMIN_BOOTSTRAP_* "
                    + "variables from this environment now.", properties.email());
            case ALREADY_ADMIN -> log.info("Bootstrap administrator {} already exists; nothing changed. "
                    + "The ADMIN_BOOTSTRAP_* variables can be removed.", properties.email());
            case CONFLICT -> log.warn("Bootstrap administrator not created: {} or its phone number already "
                    + "belongs to a non-admin account. Nothing was changed.", properties.email());
        }
    }
}
