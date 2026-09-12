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
import com.tutorspoint.common.exception.AuthenticationFailedException;
import com.tutorspoint.common.exception.BusinessRuleViolationException;

/**
 * Everything a visitor can do before they are signed in, and the account state machine
 * behind it (FR-A1 – FR-A6).
 *
 * <pre>
 *   register --> PENDING_VERIFICATION --+-- verify-email --+--> ACTIVE --> login
 *                                       |                 |
 *                                       +-- verify-otp ---+
 * </pre>
 *
 * <p>Both channels must be confirmed before the account activates, in either order, and
 * only an ACTIVE account can sign in.
 */
public interface AuthService {

    /**
     * Creates a PENDING_VERIFICATION account and sends both the verification email and the
     * phone code (FR-A1).
     *
     * @throws BusinessRuleViolationException if the email or the phone number already
     *                                        belongs to an account
     */
    AccountResponse register(RegisterRequest request);

    /**
     * Confirms the emailed link and activates the account if the phone is already verified.
     *
     * @throws BusinessRuleViolationException if the token is unknown, spent or expired
     */
    void verifyEmail(VerifyEmailRequest request);

    /**
     * Sends a fresh phone code (FR-A2). Silent when the address has no account or the phone
     * is already verified — a caller must not be able to learn either fact from here.
     *
     * @throws BusinessRuleViolationException if the hourly send cap is reached
     */
    void sendOtp(SendOtpRequest request);

    /**
     * Confirms the phone code and activates the account if the email is already verified.
     *
     * @throws BusinessRuleViolationException if the code is wrong, spent, expired, or has
     *                                        run out of attempts
     */
    void verifyOtp(VerifyOtpRequest request);

    /**
     * Signs in and opens a session (FR-A4).
     *
     * @throws AuthenticationFailedException  on wrong credentials — identically whether the
     *                                        email is unknown or the password is wrong
     * @throws BusinessRuleViolationException if the account is not ACTIVE, which the client
     *                                        needs in order to send the user back to
     *                                        verification rather than to the login form
     */
    AuthTokensResponse login(LoginRequest request);

    /**
     * Exchanges a refresh token for a new pair, rotating the old one out.
     *
     * @throws AuthenticationFailedException if the token is invalid, expired, revoked, or
     *                                       the account can no longer sign in
     */
    AuthTokensResponse refresh(RefreshTokenRequest request);

    /** Ends the session the refresh token identifies. Idempotent. */
    void logout(RefreshTokenRequest request);

    /**
     * Emails a reset link if the address has an account (FR-A6). Returns the same way either
     * way: this endpoint must never reveal whether an email is registered.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Sets a new password and signs the account out everywhere, because a reset is how
     * somebody recovers an account they had lost control of.
     *
     * @throws BusinessRuleViolationException if the token is unknown, spent or expired
     */
    void resetPassword(ResetPasswordRequest request);
}
