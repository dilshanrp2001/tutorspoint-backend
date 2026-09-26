package com.tutorspoint.auth;

import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.UpdateAccountRequest;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own account (FR-A7).
 *
 * <p>No path variable names an account, on purpose: the subject of every request here is the
 * token's owner, so there is no id for a caller to swap for somebody else's.
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account", description = "The signed-in user's own account")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "View my account")
    public ApiResponse<AccountResponse> myAccount() {
        return ApiResponse.ok(accountService.myAccount());
    }

    @PutMapping
    @Operation(summary = "Edit my details",
            description = "Name and preferred language. Changing a verified email address or "
                    + "phone number is a re-verification flow, not a field edit.")
    public ApiResponse<AccountResponse> updateMyAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.ok(accountService.updateMyAccount(request));
    }

    @DeleteMapping
    @Operation(summary = "Delete my account",
            description = "Soft delete: the account can no longer sign in and every session "
                    + "ends, while the row survives so past enquiries and reviews keep their author.")
    public ApiResponse<Void> deleteMyAccount() {
        accountService.deleteMyAccount();
        return ApiResponse.ok();
    }
}
