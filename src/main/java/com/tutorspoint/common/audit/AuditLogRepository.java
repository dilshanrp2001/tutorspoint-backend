package com.tutorspoint.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** The audit trail. Written by {@link AuditLogListener}; never updated, never deleted. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Everything recorded against one row, newest first. */
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditTargetType targetType, Long targetId);
}
