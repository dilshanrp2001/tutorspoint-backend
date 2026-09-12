package com.tutorspoint.auth;

import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.AuthTokensResponse;
import com.tutorspoint.auth.dto.ForgotPasswordRequest;
import com.tutorspoint.auth.dto.LoginRequest;
import com.tutorspoint.auth.dto.RefreshTokenRequest;
import com.tutorspoint.auth.dto.RegisterRequest;
import com.tutorspoint.auth.dto.ResetPasswordRequest;
import com.tutorspoint.auth.dto.SendOtpRequest;
import com.tutorspoint.auth.dto.VerifyEmailRequest;
import com.tutorspoint.auth.dto.VerifyOtpRequest;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of the API: everything a visitor does before they hold a token
 * (FR-A1 – FR-A6).
 *
 * <p>No logic lives here. Each method validates its body, hands it to {@link AuthService} and
 * wraps the answer — no branching, no try/catch, no decision about what a failure means.
 *
 * <p>All of it is POST, including verification: these requests carry secrets, and a secret in
 * a query string ends up in browser history, proxy logs and access logs. The emailed link
 * points at a frontend page, which posts the token here.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, verification, sign-in and password recovery")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as a tutor or a parent",
            description = "Creates a PENDING_VERIFICATION account, then emails a verification "
                    + "link and texts a six-digit code. The account activates once both are confirmed.")
    public ApiResponse<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address with the token from the emailed link")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.ok();
    }

    @PostMapping("/request-otp")
    @Operation(summary = "Send a fresh phone verification code",
            description = "Succeeds regardless of whether the address has an account, so it "
                    + "cannot be used to discover who is registered. Capped per account per hour.")
    public ApiResponse<Void> requestOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ApiResponse.ok();
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Confirm a phone number with the six-digit code")
    public ApiResponse<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = "Returns a short-lived access token and a revocable refresh token.")
    public ApiResponse<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new pair",
            description = "The presented refresh token is revoked; the one returned replaces it.")
    public ApiResponse<AuthTokensResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session the refresh token identifies")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a password reset link",
            description = "Always succeeds, whether or not the address has an account.")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password with the token from the emailed link",
            description = "Signs the account out of every existing session.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }
}
