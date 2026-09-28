package com.tutorspoint.common;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every controller method has an explicit authorization rule, and this test fails for one that
 * does not.
 *
 * <p>Not a list of URLs to try: the endpoints come from Spring MVC's own handler mappings, so a
 * controller method added tomorrow is checked tomorrow without anybody editing this class. For
 * each one:
 *
 * <ul>
 *   <li><strong>A guest reaches it only if it is declared public below.</strong> An endpoint that
 *       a request with no token gets through to, and that is not in {@link #PUBLIC_ENDPOINTS},
 *       fails - that is a route somebody opened by accident, typically by putting it under a
 *       permitted prefix.</li>
 *   <li><strong>Anything else is reached by at least one role.</strong> {@code SecurityConfig}
 *       ends in {@code denyAll()}, so a new endpoint nobody wrote a rule for is unreachable by
 *       everyone; this fails for it, instead of letting it ship as dead code or prompting
 *       somebody to "fix" it with a blanket {@code authenticated()}.</li>
 * </ul>
 *
 * <p>"Reached" means the request got through the security filter chain to the dispatcher and was
 * matched to its handler - not that it succeeded. A request with no body still counts, and so
 * does one the handler's {@code @PreAuthorize} then refuses: the route rule let it in, and the
 * method rule is a second, separate check that the other authorization tests cover.
 *
 * <p>Transactional, because reaching a handler runs it: the delete-my-account endpoint really
 * deletes the account the token names, and the rollback undoes it.
 */
@Transactional
class EndpointAuthorizationIT extends AbstractIntegrationTest {

    /**
     * Everything a guest may reach, as "METHOD pattern". Adding to this list is a decision to
     * publish an endpoint, and should read like one in review.
     */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "POST /api/auth/register",
            "POST /api/auth/verify-email",
            "POST /api/auth/request-otp",
            "POST /api/auth/verify-otp",
            "POST /api/auth/login",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "POST /api/auth/forgot-password",
            "POST /api/auth/reset-password",
            "GET /api/reference",
            "GET /api/reference/subjects",
            "GET /api/reference/exam-levels",
            "GET /api/reference/syllabuses",
            "GET /api/reference/areas",
            "GET /api/reference/mediums",
            "GET /api/reference/class-formats",
            "GET /api/search/tutors",
            "GET /api/tutors/{tutorId}",
            "GET /api/media/**");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtService jwtService;

    private final Map<String, String> tokensByRole = new LinkedHashMap<>();

    @BeforeEach
    void oneAccountPerRole() {
        tokensByRole.put("TUTOR", tokenFor(new Tutor("authz.tutor@example.lk", "unused", "Tutor", "+94775100001", Language.EN)));
        tokensByRole.put("PARENT", tokenFor(new Parent("authz.parent@example.lk", "unused", "Parent", "+94775100002", Language.EN)));
        tokensByRole.put("ADMIN", tokenFor(new Admin("authz.admin@example.lk", "unused", "Admin", "+94775100003", Language.EN)));
    }

    @Test
    @DisplayName("a guest reaches exactly the endpoints declared public, and every other endpoint admits some role")
    void everyEndpointHasAnExplicitRule() throws Exception {
        List<Endpoint> endpoints = applicationEndpoints();
        assertThat(endpoints).as("endpoints discovered from the handler mappings").hasSizeGreaterThan(40);

        List<String> problems = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            seen.add(endpoint.name());
            boolean declaredPublic = PUBLIC_ENDPOINTS.contains(endpoint.name());
            boolean guestReaches = reaches(endpoint, null);

            if (guestReaches && !declaredPublic) {
                problems.add(endpoint.name() + " is reachable without a token but is not declared public");
            } else if (!guestReaches && declaredPublic) {
                problems.add(endpoint.name() + " is declared public but a guest cannot reach it");
            } else if (!declaredPublic && !reachedByAnyRole(endpoint)) {
                problems.add(endpoint.name() + " has no authorization rule: no role can reach it");
            }
        }

        Set<String> stale = new TreeSet<>(PUBLIC_ENDPOINTS);
        stale.removeAll(seen);
        stale.forEach(name -> problems.add(name + " is declared public but no longer exists"));

        assertThat(problems).as("authorization problems").isEmpty();
    }

    private boolean reachedByAnyRole(Endpoint endpoint) throws Exception {
        for (String token : tokensByRole.values()) {
            if (reaches(endpoint, token)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the request got through the security chain to its handler. */
    private boolean reaches(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder request = endpoint.multipart()
                ? multipart(endpoint.method(), endpoint.samplePath())
                : request(endpoint.method(), endpoint.samplePath()).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getHandler() != null;
    }

    /** The application's own endpoints: springdoc's are configured separately, and not ours. */
    private List<Endpoint> applicationEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.tutorspoint")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            assertThat(methods).as("HTTP method of %s", entry.getValue()).isNotEmpty();
            boolean multipart = info.getConsumesCondition().getConsumableMediaTypes()
                    .contains(MediaType.MULTIPART_FORM_DATA);
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                for (RequestMethod method : methods) {
                    endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), pattern, multipart));
                }
            }
        }
        return endpoints;
    }

    private String tokenFor(User user) {
        user.verifyEmail();
        user.verifyPhone();
        user.activate();
        return jwtService.issueAccessToken(users.saveAndFlush(user));
    }

    private record Endpoint(HttpMethod method, String pattern, boolean multipart) {

        String name() {
            return method.name() + " " + pattern;
        }

        /** A concrete URL the pattern matches: every variable a 1, a trailing wildcard a filename. */
        String samplePath() {
            return pattern.replaceAll("\\{[^}]+}", "1").replace("**", "sample.jpg");
        }
    }
}
