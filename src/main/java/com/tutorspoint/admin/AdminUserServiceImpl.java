package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminUserCriteria;
import com.tutorspoint.admin.dto.AdminUserPage;
import com.tutorspoint.admin.dto.AdminUserSummary;
import com.tutorspoint.admin.event.AccountModeratedEvent;
import com.tutorspoint.admin.repository.AdminUserRepository;
import com.tutorspoint.admin.repository.AdminUserSpecifications;
import com.tutorspoint.auth.RefreshTokenService;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account moderation.
 *
 * <p>Every change publishes an {@link AccountModeratedEvent}, which is how it reaches the audit
 * record: this class never writes an audit row itself, and the listener writes it inside this
 * transaction, so a suspension cannot take effect unrecorded.
 *
 * <p>Suspension also ends every session. The account's access tokens stay valid until they
 * expire - at most the access-token lifetime - but no refresh can extend them, and login and
 * enquiry creation both re-check the status on the way in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserServiceImpl implements AdminUserService {

    private static final String ERROR_ADMIN_NOT_MODERATABLE = "ADMIN_ACCOUNT_NOT_MODERATABLE";

    private final AdminUserRepository users;
    private final RefreshTokenService refreshTokens;
    private final AdminMapper adminMapper;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public AdminUserPage users(AdminUserCriteria criteria) {
        PageRequest pageable = PageRequest.of(criteria.page(), criteria.size(),
                // The id breaks ties, so rows registered in the same instant never swap pages.
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<User> found = users.findAll(AdminUserSpecifications.matching(
                criteria.role(), criteria.status(), criteria.q(), criteria.pendingDocuments()), pageable);
        return new AdminUserPage(found.map(adminMapper::toSummary).getContent(),
                found.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
    }

    @Override
    @Transactional
    public AdminUserSummary suspend(Long userId, String reason) {
        User user = requireUser(userId);
        // Admins are not moderated from this screen - including by themselves, which would
        // otherwise be one click from locking the pilot's only operator out.
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessRuleViolationException(ERROR_ADMIN_NOT_MODERATABLE,
                    "Account %s is an administrator and cannot be suspended here".formatted(userId));
        }
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            return adminMapper.toSummary(user);
        }

        AccountStatus before = user.getStatus();
        user.suspend();
        refreshTokens.revokeAllFor(userId);
        events.publishEvent(new AccountModeratedEvent(currentUser.requireId(), userId, before, user.getStatus(), reason));
        log.info("Account {} suspended", userId);
        return adminMapper.toSummary(user);
    }

    @Override
    @Transactional
    public AdminUserSummary reinstate(Long userId, String reason) {
        User user = requireUser(userId);
        AccountStatus before = user.getStatus();
        user.reinstate();
        events.publishEvent(new AccountModeratedEvent(currentUser.requireId(), userId, before, user.getStatus(), reason));
        log.info("Account {} reinstated as {}", userId, user.getStatus());
        return adminMapper.toSummary(user);
    }

    private User requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account", userId));
    }
}
