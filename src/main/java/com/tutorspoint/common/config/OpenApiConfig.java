package com.tutorspoint.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata for the Swagger UI served at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tutorsPointOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TutorsPoint API")
                .version("v1")
                .description("Verification-first tutor-discovery marketplace for the Sri Lankan market.")
                .contact(new Contact().name("TutorsPoint").url("https://tutorspoint.xyz")));
    }
}
