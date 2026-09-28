package com.tutorspoint.auth.security;

import com.tutorspoint.auth.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Who the current request belongs to, as carried in the access token: enough to authorize
 * a call and nothing more.
 *
 * <p>This is the security principal, not the account. It is reconstructed from a signed
 * token without touching the database, so it holds no password hash, no verification state
 * and no mutable profile data — code that needs any of those loads the
 * {@link com.tutorspoint.auth.domain.User}.
 */
public record AuthenticatedUser(Long userId, String email, Role role) {

    /** Spring Security convention: hasRole('PARENT') looks for the authority ROLE_PARENT. */
    private static final String AUTHORITY_PREFIX = "ROLE_";

    public AuthenticatedUser {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(AUTHORITY_PREFIX + role.name()));
    }

    /** True when the given owner id is this caller. The one comparison ownership means. */
    public boolean owns(Long ownerId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
