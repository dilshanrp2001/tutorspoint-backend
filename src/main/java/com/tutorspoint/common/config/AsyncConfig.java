package com.tutorspoint.common.config;

import com.tutorspoint.common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on {@code @Async} method execution. Notifications use it so that a slow SMTP
 * server or SMS gateway cannot hold a request thread — or a database transaction —
 * open while it times out. The pool itself is configured under {@code spring.task.execution}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Picked up by Spring Boot's auto-configured executor, so the request id travels with every
     * task handed to the pool.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
