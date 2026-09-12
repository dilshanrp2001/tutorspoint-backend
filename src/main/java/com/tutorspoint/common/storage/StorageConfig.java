package com.tutorspoint.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the storage settings.
 *
 * <p>There is no {@code @Bean} choosing an implementation here, and there should not be:
 * {@link LocalDiskFileStorage} is a {@code @Component}, and the day an object-store
 * implementation lands, the choice between them belongs behind a {@code @ConditionalOnProperty}
 * on the implementations themselves rather than in a factory that has to be edited to learn
 * about each new one.
 */
@Configuration
@EnableConfigurationProperties(LocalDiskStorageProperties.class)
public class StorageConfig {
}
