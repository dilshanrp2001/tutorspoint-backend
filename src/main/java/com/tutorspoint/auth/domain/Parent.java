package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A parent looking for a tutor for their children — one of the two kinds of {@link Seeker}.
 * A learner acting for themselves registers as a {@link Student} instead.
 *
 * <p>A parent owns any number of {@link ChildProfile}s (FR-A5): one account, one login,
 * a sub-profile per child so enquiries carry the right grade and exam level. The children
 * are part of this aggregate, which is why they are created and removed through the
 * parent rather than constructed loose and wired up by a caller.
 */
@Entity
@Table(name = "parents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parent extends Seeker {

    private static final String ERROR_CHILD_NOT_OWNED = "CHILD_PROFILE_NOT_OWNED";

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChildProfile> children = new LinkedHashSet<>();

    public Parent(String email,
                  String passwordHash,
                  String fullName,
                  String phoneNumber,
                  Language preferredLanguage) {
        super(Role.PARENT, email, passwordHash, fullName, phoneNumber, preferredLanguage);
    }

    /** Read-only: children are added and removed through this entity, never by a caller. */
    public Set<ChildProfile> getChildren() {
        return Collections.unmodifiableSet(children);
    }

    /**
     * Adds a child sub-profile (FR-A5) and returns it so the caller can read its id after
     * the flush. {@code school} and {@code notes} are optional.
     */
    public ChildProfile addChild(String name, String grade, String examLevel, String school, String notes) {
        ensureNotDeleted();
        ChildProfile child = new ChildProfile(this, name, grade, examLevel, school, notes);
        children.add(child);
        return child;
    }

    /**
     * Removes one of this parent's children; {@code orphanRemoval} deletes the row.
     *
     * @throws BusinessRuleViolationException if the profile belongs to someone else — the
     *                                        entity checks ownership itself rather than
     *                                        trusting the service to have done it
     */
    public void removeChild(ChildProfile child) {
        ensureNotDeleted();
        if (child == null || !children.remove(child)) {
            throw new BusinessRuleViolationException(ERROR_CHILD_NOT_OWNED,
                    "Child profile %s does not belong to account %s"
                            .formatted(child == null ? null : child.getId(), getId()));
        }
    }
}
