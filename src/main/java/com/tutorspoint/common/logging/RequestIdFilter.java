package com.tutorspoint.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id, puts it in the logging context for the lifetime of the
 * request, and returns it in {@value #HEADER}.
 *
 * <p>First in the chain - ahead of Spring Security - so that even a request rejected for its
 * token or its rate has an id on every line it logs. {@code MdcTaskDecorator} carries the id
 * onto the {@code @Async} threads the request hands work to, so a notification that fails ten
 * seconds later still names the request that caused it.
 *
 * <p>An id sent by the caller (the reverse proxy, or the frontend) is reused so one id follows a
 * request across systems, but only if it looks like an id. Anything else is replaced: a value
 * that goes verbatim into every log line must not be able to carry a newline, or JSON, or a
 * megabyte of text.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** The key in the logging context, and therefore the field name in the JSON log. */
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTABLE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = acceptableOrNew(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Container threads are pooled: an id left behind would label the next request's lines.
            MDC.remove(MDC_KEY);
        }
    }

    static String acceptableOrNew(String supplied) {
        return supplied != null && ACCEPTABLE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
    }
}
