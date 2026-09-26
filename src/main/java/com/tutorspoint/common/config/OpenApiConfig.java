package com.tutorspoint.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the Swagger UI served at /swagger-ui.html.
 *
 * <p>Also declares the bearer scheme the protected endpoints reference, so the docs carry an
 * Authorize box and the generated client knows an access token belongs in a header. The
 * frontend derives its request and response types from this contract, so what is described
 * here is the API, not a comment about it.
 */
@Configuration
public class OpenApiConfig {

    /** Referenced by {@code @SecurityRequirement(name = "bearerAuth")} on the controllers. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tutorsPointOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TutorsPoint API")
                        .version("v1")
                        .description("Verification-first tutor-discovery marketplace for the Sri Lankan market.")
                        .contact(new Contact().name("TutorsPoint").url("https://tutorspoint.xyz")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/auth/login")));
    }
}
