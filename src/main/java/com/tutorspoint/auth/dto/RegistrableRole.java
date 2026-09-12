package com.tutorspoint.auth.dto;

import com.tutorspoint.auth.domain.Role;

/**
 * The roles a visitor may choose at registration (FR-A1).
 *
 * <p>A separate enum from {@link Role} precisely because ADMIN is missing: admins are
 * provisioned, never self-registered. Modelling that as the subset the request body binds
 * to means deserialization enforces it at the edge, and no service has to remember to.
 */
public enum RegistrableRole {

    TUTOR(Role.TUTOR),
    PARENT(Role.PARENT);

    private final Role role;

    RegistrableRole(Role role) {
        this.role = role;
    }

    public Role toRole() {
        return role;
    }
}
