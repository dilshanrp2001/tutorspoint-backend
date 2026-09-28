package com.tutorspoint.admin.event;

import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;

import java.time.Instant;

/**
 * A tutor's verified badge was granted or withdrawn (FR-R3).
 *
 * <p>Only published for a real change: re-verifying a verified tutor is not an event.
 *
 * @param adminId            who decided
 * @param tutorId            the tutor
 * @param profileId          the profile carrying the badge, which is the audited row
 * @param verified           the badge's new state
 * @param previousVerifiedAt when it was granted before this change, or null
 * @param verifiedAt         when it is granted from now, or null when withdrawn
 */
public record TutorVerificationChangedEvent(
        Long adminId,
        Long tutorId,
        Long profileId,
        boolean verified,
        Instant previousVerifiedAt,
        Instant verifiedAt) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        return new AuditEntry(adminId,
                verified ? AuditAction.TUTOR_VERIFIED : AuditAction.TUTOR_VERIFICATION_REVOKED,
                AuditTargetType.TUTOR_PROFILE, profileId,
                AuditEntry.values("verified", !verified, "verifiedAt", previousVerifiedAt),
                AuditEntry.values("verified", verified, "verifiedAt", verifiedAt, "tutorId", tutorId),
                null);
    }
}
