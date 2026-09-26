package com.tutorspoint.notification.config;

import com.tutorspoint.notification.template.NotificationTemplateRenderer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * The template engine notifications render with — separate from the one Spring Boot
 * auto-configures for MVC views, because notifications need two template modes at
 * once: HTML for email bodies and TEXT for SMS, chosen by file extension.
 */
@Configuration
@EnableConfigurationProperties(NotificationEmailProperties.class)
public class NotificationConfig {

    /**
     * Still a {@link SpringTemplateEngine}, so expressions stay SpEL — the dialect
     * Boot's Thymeleaf starter is packaged for. It needs no application context, so a
     * unit test builds this exact engine and renders the real templates.
     */
    @Bean
    public ITemplateEngine notificationTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
        return engine;
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(NotificationTemplateRenderer.TEMPLATE_ROOT);
        // Template names already carry their extension, which is how a resolver knows
        // whether the body it is about to render is markup or plain text.
        resolver.setSuffix("");
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setOrder(order);
        resolver.setCacheable(true);
        return resolver;
    }
}
