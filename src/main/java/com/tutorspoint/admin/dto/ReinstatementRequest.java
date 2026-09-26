package com.tutorspoint.admin.dto;

import com.tutorspoint.common.audit.AuditLog;
import jakarta.validation.constraints.Size;

/** Lifting a suspension. The reason is optional, and recorded when given. */
public record ReinstatementRequest(
        @Size(max = AuditLog.MAX_REASON, message = "{validation.admin.reason.size}")
        String reason) {
}
