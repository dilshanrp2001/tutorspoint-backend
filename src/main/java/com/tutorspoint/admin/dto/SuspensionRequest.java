package com.tutorspoint.admin.dto;

import com.tutorspoint.common.audit.AuditLog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Taking an account or a profile down. The reason is required: it is written to the audit
 * record, and "why was this tutor removed" is the first question anybody will ask of it.
 */
public record SuspensionRequest(
        @NotBlank(message = "{validation.admin.reason.required}")
        @Size(max = AuditLog.MAX_REASON, message = "{validation.admin.reason.size}")
        String reason) {
}
