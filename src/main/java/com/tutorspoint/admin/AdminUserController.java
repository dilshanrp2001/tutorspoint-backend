package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminUserCriteria;
import com.tutorspoint.admin.dto.AdminUserPage;
import com.tutorspoint.admin.dto.AdminUserSummary;
import com.tutorspoint.admin.dto.ReinstatementRequest;
import com.tutorspoint.admin.dto.SuspensionRequest;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account search and moderation. {@code ROLE_ADMIN} only, at the route and at the service. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: accounts", description = "Finding, suspending and reinstating accounts")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List accounts",
            description = "Every account matching all filters given, newest first. q matches a fragment of "
                    + "the name, email address or phone number.")
    public ApiResponse<AdminUserPage> users(@Valid @ParameterObject AdminUserCriteria criteria) {
        return ApiResponse.ok(adminUserService.users(criteria));
    }

    @PostMapping("/{userId}/suspend")
    @Operation(summary = "Suspend an account",
            description = "Blocks sign-in and ends every session. Audited with the reason given. "
                    + "Administrator accounts cannot be suspended here.")
    public ApiResponse<AdminUserSummary> suspend(@PathVariable Long userId,
                                                 @Valid @RequestBody SuspensionRequest request) {
        return ApiResponse.ok(adminUserService.suspend(userId, request.reason()));
    }

    @PostMapping("/{userId}/reinstate")
    @Operation(summary = "Reinstate an account",
            description = "Lifts a suspension. The account returns to ACTIVE, or to PENDING_VERIFICATION "
                    + "if it had not finished verifying. Audited.")
    public ApiResponse<AdminUserSummary> reinstate(@PathVariable Long userId,
                                                   @Valid @RequestBody(required = false) ReinstatementRequest request) {
        return ApiResponse.ok(adminUserService.reinstate(userId, request == null ? null : request.reason()));
    }
}
