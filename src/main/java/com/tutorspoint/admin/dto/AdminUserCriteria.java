package com.tutorspoint.admin.dto;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The account list's query string. Every filter is optional; none given lists everybody,
 * newest first.
 */
public record AdminUserCriteria(
        @Parameter(description = "Only this role")
        Role role,

        @Parameter(description = "Only this account status")
        AccountStatus status,

        @Parameter(description = "A fragment of the name, email address or phone number")
        @Size(max = 100, message = "{validation.admin.query.size}")
        String q,

        @Parameter(description = "Only tutors with at least one document waiting for review")
        Boolean pendingDocuments,

        @Parameter(description = "Zero-based page number")
        @PositiveOrZero(message = "{validation.admin.page.range}")
        Integer page,

        @Parameter(description = "Rows per page, 1 to " + AdminUserCriteria.MAX_SIZE)
        @Min(value = 1, message = "{validation.admin.size.range}")
        @Max(value = AdminUserCriteria.MAX_SIZE, message = "{validation.admin.size.range}")
        Integer size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public AdminUserCriteria {
        page = page == null ? 0 : page;
        size = size == null ? DEFAULT_SIZE : size;
        pendingDocuments = Boolean.TRUE.equals(pendingDocuments);
    }
}
