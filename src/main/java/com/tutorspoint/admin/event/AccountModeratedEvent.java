package com.tutorspoint.admin.event;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;

/**
 * An account was suspended or reinstated by an administrator.
 *
 * @param adminId who acted
 * @param userId  the account, which is the audited row
 * @param before  its status before
 * @param after   its status after: SUSPENDED, or where verification had got to once reinstated
 * @param reason  the moderator's stated reason
 */
public record AccountModeratedEvent(
        Long adminId,
        Long userId,
        AccountStatus before,
        AccountStatus after,
        String reason) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        return new AuditEntry(adminId,
                after == AccountStatus.SUSPENDED ? AuditAction.ACCOUNT_SUSPENDED : AuditAction.ACCOUNT_REINSTATED,
                AuditTargetType.USER, userId,
                AuditEntry.values("status", before),
                AuditEntry.values("status", after),
                reason);
    }
}
