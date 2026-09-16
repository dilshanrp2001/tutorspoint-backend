package com.tutorspoint.common.audit;

import com.tutorspoint.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * One audited action (NFR-10): who did what to which row, what it changed, from where, and
 * when — {@code createdAt} is the moment.
 *
 * <p>Immutable in every layer that can make it so. There are no mutators here, Hibernate is
 * told never to issue an update, and the table's trigger refuses one from anywhere else.
 *
 * <p>The actor and the target are ids rather than associations. Nothing navigates from an
 * audit row into the domain, and a record must survive whatever later happens to the rows it
 * describes.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseEntity {

    public static final int MAX_REASON = 1000;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", updatable = false)
    private Map<String, Object> beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", updatable = false)
    private Map<String, Object> afterValue;

    @Column(name = "reason", length = MAX_REASON, updatable = false)
    private String reason;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    public AuditLog(AuditEntry entry, String ipAddress) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        this.actorId = entry.actorId();
        this.action = entry.action();
        this.targetType = entry.targetType();
        this.targetId = entry.targetId();
        this.beforeValue = entry.before().isEmpty() ? null : entry.before();
        this.afterValue = entry.after().isEmpty() ? null : entry.after();
        this.reason = trimToLimit(entry.reason());
        this.ipAddress = ipAddress;
    }

    private static String trimToLimit(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > MAX_REASON ? trimmed.substring(0, MAX_REASON) : trimmed;
    }
}
