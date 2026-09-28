package com.tutorspoint.common.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads the client address from the request bound to this thread.
 *
 * <p>Deliberately {@code getRemoteAddr()} and never a raw {@code X-Forwarded-For}: that header
 * is whatever the client chose to send, and an audit record must not be forgeable by the person
 * it records. Behind the Phase 5.4 reverse proxy, the remote address becomes the client's once
 * {@code server.forward-headers-strategy} is set to trust that proxy - a deployment decision,
 * made there, not guessed at here.
 */
@Component
public class ServletRequestOrigin implements RequestOrigin {

    @Override
    public Optional<String> clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.ofNullable(attributes.getRequest().getRemoteAddr());
        }
        return Optional.empty();
    }
}
