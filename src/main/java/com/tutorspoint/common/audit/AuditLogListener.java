package com.tutorspoint.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every {@link AuditableEvent} published anywhere in the application.
 *
 * <p><strong>A plain {@code @EventListener}, not an after-commit one, and that is the design.</strong>
 * It runs synchronously on the publishing thread, inside the publisher's transaction, so the
 * action and its audit row commit or roll back together. An after-commit listener would allow
 * the one outcome an audit trail exists to rule out: a verification or a suspension that took
 * effect with no record of it, because the audit write failed after the fact. Here, if the
 * record cannot be written, the action does not happen.
 *
 * <p>Running on the request thread is also what makes the client address available at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogListener {

    private final AuditLogRepository auditLogs;
    private final RequestOrigin requestOrigin;

    @EventListener
    public void record(AuditableEvent event) {
        AuditEntry entry = event.auditEntry();
        auditLogs.save(new AuditLog(entry, requestOrigin.clientAddress().orElse(null)));
        log.info("Audit: account {} {} {} {}",
                entry.actorId(), entry.action(), entry.targetType(), entry.targetId());
    }
}
