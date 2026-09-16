package com.tutorspoint.admin.repository;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.VerificationDocument;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The account list's filters, each optional and all combined with AND.
 *
 * <p>Lives beside the repository because it is query construction, and query construction stays
 * in the repository layer (architecture section 2).
 */
public final class AdminUserSpecifications {

    private AdminUserSpecifications() {
    }

    /**
     * @param role                 only this role, or every role when null
     * @param status               only this status, or every status when null
     * @param text                 a fragment of the name, email or phone number, or null
     * @param pendingDocumentsOnly only tutors with a document still waiting for review - the
     *                             reviewer's queue
     */
    public static Specification<User> matching(Role role, AccountStatus status, String text,
                                               boolean pendingDocumentsOnly) {
        return (user, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(user.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(user.get("status"), status));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), pattern, '\\'),
                        // Stored lower-cased by the entity, so no lower() and the index can help.
                        cb.like(user.get("email"), pattern, '\\'),
                        cb.like(user.get("phoneNumber"), pattern, '\\')));
            }
            if (pendingDocumentsOnly) {
                Subquery<Long> pending = query.subquery(Long.class);
                var document = pending.from(VerificationDocument.class);
                pending.select(document.get("id")).where(
                        cb.equal(document.get("tutor").get("id"), user.get("id")),
                        cb.equal(document.get("status"), DocumentStatus.PENDING));
                predicates.add(cb.exists(pending));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A search for "50%" means the characters, not a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
