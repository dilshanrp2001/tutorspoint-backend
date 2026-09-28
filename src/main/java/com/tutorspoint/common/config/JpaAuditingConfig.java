package com.tutorspoint.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Activates the auditing listener that populates BaseEntity createdAt / updatedAt. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
