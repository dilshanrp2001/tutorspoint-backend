package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminUserCriteria;
import com.tutorspoint.admin.dto.AdminUserPage;
import com.tutorspoint.admin.dto.AdminUserSummary;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;

/** Finding accounts and moderating them. Administrators only. */
public interface AdminUserService {

    /** Accounts matching every filter given, newest first. */
    AdminUserPage users(AdminUserCriteria criteria);

    /**
     * Blocks an account and signs it out everywhere. Idempotent for an account already suspended.
     *
     * @throws ResourceNotFoundException      if there is no such account
     * @throws BusinessRuleViolationException if the account is an administrator's, or deleted
     */
    AdminUserSummary suspend(Long userId, String reason);

    /**
     * Lifts a suspension.
     *
     * @throws ResourceNotFoundException      if there is no such account
     * @throws BusinessRuleViolationException if the account is not suspended
     */
    AdminUserSummary reinstate(Long userId, String reason);
}
