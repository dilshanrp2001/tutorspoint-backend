package com.tutorspoint.enquiry.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds and validates the enquiry module's configuration at startup. */
@Configuration
@EnableConfigurationProperties(EnquiryProperties.class)
public class EnquiryConfig {
}
