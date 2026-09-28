package com.tutorspoint.common.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one audited action changed, as the event that describes it reports it.
 *
 * <p>Carries no timestamp and no IP address. Both are facts about the moment the record is
 * written, not about the domain change, and they are added by {@link AuditLogListener} — so no
 * publisher can get them wrong or forget them.
 *
 * @param actorId    the account that acted
 * @param action     what was done
 * @param targetType what kind of row it was done to
 * @param targetId   which row
 * @param before     the changed fields as they were; empty for an action that changes nothing,
 *                   such as viewing a document
 * @param after      the changed fields as they became
 * @param reason     the moderator's stated reason, or null
 */
public record AuditEntry(
        Long actorId,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason) {

    public AuditEntry {
        if (actorId == null || action == null || targetType == null || targetId == null) {
            throw new IllegalArgumentException("An audit entry needs an actor, an action and a target");
        }
        before = before == null ? Map.of() : Collections.unmodifiableMap(before);
        after = after == null ? Map.of() : Collections.unmodifiableMap(after);
    }

    /**
     * An ordered snapshot from alternating names and values: {@code values("verified", true)}.
     *
     * <p>Values are reduced to what JSON holds without a type registry — an {@link Instant} or
     * an enum becomes its string form — so the record reads the same whatever serialiser writes
     * it. Nulls are kept: "verifiedAt was null" is a fact worth recording.
     *
     * @throws IllegalArgumentException if the arguments are not name/value pairs
     */
    public static Map<String, Object> values(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected name/value pairs, got " + namesAndValues.length + " arguments");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            snapshot.put(String.valueOf(namesAndValues[i]), plain(namesAndValues[i + 1]));
        }
        return snapshot;
    }

    private static Object plain(Object value) {
        if (value instanceof Instant || value instanceof Enum<?>) {
            return value.toString();
        }
        return value;
    }
}
