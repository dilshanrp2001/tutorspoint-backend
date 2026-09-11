package com.tutorspoint.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on {@code @Async} method execution. Notifications use it so that a slow SMTP
 * server or SMS gateway cannot hold a request thread — or a database transaction —
 * open while it times out. The pool itself is configured under {@code spring.task.execution}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
