package com.tutorspoint.auth.security;

import com.tutorspoint.common.exception.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of Spring Security's context, where {@link JwtAuthenticationFilter}
 * put it. The only class that knows the principal arrives through a thread-local.
 */
@Component
public class SecurityContextCurrentUser implements CurrentUser {

    private static final String ERROR_NOT_AUTHENTICATED = "NOT_AUTHENTICATED";

    @Override
    public AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser caller)) {
            throw new AuthenticationFailedException(ERROR_NOT_AUTHENTICATED,
                    "This endpoint requires a signed-in user");
        }
        return caller;
    }
}
