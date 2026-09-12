package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.UpdateAccountRequest;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account management.
 *
 * <p>{@code @PreAuthorize} here is a second gate, not the only one: the route rules already
 * require authentication for {@code /api/account}. Both exist because the method-level rule
 * travels with the service — a later caller that is not an HTTP request cannot sidestep it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AccountServiceImpl implements AccountService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AccountMapper accountMapper;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public AccountResponse myAccount() {
        return accountMapper.toAccountResponse(requireCaller());
    }

    @Override
    @Transactional
    public AccountResponse updateMyAccount(UpdateAccountRequest request) {
        User user = requireCaller();
        user.changeFullName(request.fullName());
        user.changePreferredLanguage(request.preferredLanguage());
        log.info("Account {} updated its details", user.getId());
        return accountMapper.toAccountResponse(user);
    }

    @Override
    @Transactional
    public void deleteMyAccount() {
        User user = requireCaller();
        user.delete();
        refreshTokenService.revokeAllFor(user.getId());
        log.info("Account {} deleted at the owner's request", user.getId());
    }

    /**
     * The caller's own account row.
     *
     * <p>Loading by the id in the token is what makes every method here owner-scoped: the
     * request never names an account, so there is nothing to tamper with. A token that
     * survives its account — deleted while a short-lived access token was still live — lands
     * as a 404 rather than a 500.
     */
    private User requireCaller() {
        Long callerId = currentUser.requireId();
        return users.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", callerId));
    }
}
