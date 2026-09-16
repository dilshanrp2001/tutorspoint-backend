package com.tutorspoint.admin.event;

import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;
import com.tutorspoint.tutor.domain.ProfileStatus;

/**
 * A tutor profile was suspended or reinstated by an administrator.
 *
 * @param adminId   who acted
 * @param tutorId   the tutor
 * @param profileId the profile, which is the audited row
 * @param before    its status before
 * @param after     its status after: SUSPENDED, or DRAFT once reinstated
 * @param reason    the moderator's stated reason
 */
public record ProfileModeratedEvent(
        Long adminId,
        Long tutorId,
        Long profileId,
        ProfileStatus before,
        ProfileStatus after,
        String reason) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        return new AuditEntry(adminId,
                after == ProfileStatus.SUSPENDED ? AuditAction.PROFILE_SUSPENDED : AuditAction.PROFILE_REINSTATED,
                AuditTargetType.TUTOR_PROFILE, profileId,
                AuditEntry.values("status", before),
                AuditEntry.values("status", after, "tutorId", tutorId),
                reason);
    }
}
