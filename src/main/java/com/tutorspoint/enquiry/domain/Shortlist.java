package com.tutorspoint.enquiry.domain;

import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A tutor a parent has saved to come back to (FR-P1), with the parent's own note about why.
 *
 * <p>In the enquiry package rather than a package of its own because a shortlist is the step
 * before an enquiry: a parent shortlists three tutors, then enquires to two of them, and the
 * two features are read together on every screen that shows either.
 *
 * <p><strong>The note is private to the parent.</strong> It is a reminder — "cheaper, but
 * further away" — not feedback, and it never appears in anything the tutor can read. Nothing
 * here is masked, because saving a tutor is not contacting one: a shortlist entry carries no
 * contact details at all.
 */
@Entity
@Table(name = "shortlists",
        uniqueConstraints = @UniqueConstraint(name = "uq_shortlists_pair", columnNames = {"parent_id", "tutor_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shortlist extends BaseEntity {

    /** Comfortably more than a reminder needs, and bounded so the column is not an essay store. */
    public static final int MAX_NOTE_LENGTH = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false, updatable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false, updatable = false)
    private Tutor tutor;

    @Column(name = "note", length = MAX_NOTE_LENGTH)
    private String note;

    public Shortlist(Parent parent, Tutor tutor, String note) {
        this.parent = required(parent, "parent");
        this.tutor = required(tutor, "tutor");
        this.note = normaliseNote(note);
    }

    /**
     * Rewrites the parent's note. Saving the same tutor again is this, not a second row:
     * the pair is unique, and a parent who re-saves has changed their mind about the note,
     * not created another entry.
     */
    public void changeNote(String note) {
        this.note = normaliseNote(note);
    }

    private static String normaliseNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
