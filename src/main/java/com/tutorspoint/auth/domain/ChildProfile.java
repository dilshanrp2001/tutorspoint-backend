package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * A child studying under a {@link Parent} account (FR-A5). Not a user: a child never logs
 * in. The sub-profile exists so an enquiry carries the right grade and exam level without
 * the parent retyping it.
 *
 * <p>Part of the parent aggregate — instances are created through
 * {@link Parent#addChild} and removed through {@link Parent#removeChild}, which is why
 * the constructor is package-private.
 *
 * <p>{@code grade} and {@code examLevel} are free text for now. Phase 2 introduces the
 * translatable {@code ExamLevel} reference table; a later migration turns this column
 * into a foreign key to it, so that search filters and child profiles agree on one set of
 * values instead of two.
 */
@Entity
@Table(name = "child_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** School year, e.g. "Grade 11". */
    @Column(name = "grade", nullable = false, length = 50)
    private String grade;

    /** What the child is working towards, e.g. "GCE O/L". */
    @Column(name = "exam_level", nullable = false, length = 50)
    private String examLevel;

    @Column(name = "school", length = 200)
    private String school;

    @Column(name = "notes", length = 1000)
    private String notes;

    ChildProfile(Parent parent, String name, String grade, String examLevel, String school, String notes) {
        this.parent = requireNotNull(parent, "parent");
        this.name = requireText(name, "name").trim();
        this.grade = requireText(grade, "grade").trim();
        this.examLevel = requireText(examLevel, "examLevel").trim();
        this.school = trimToNull(school);
        this.notes = trimToNull(notes);
    }

    /** Edits the details a parent may change. Identity and owner are not among them. */
    public void updateDetails(String name, String grade, String examLevel, String school, String notes) {
        this.name = requireText(name, "name").trim();
        this.grade = requireText(grade, "grade").trim();
        this.examLevel = requireText(examLevel, "examLevel").trim();
        this.school = trimToNull(school);
        this.notes = trimToNull(notes);
    }

    /** Ownership check for the authorization rule that a parent sees only their own children. */
    public boolean belongsTo(Long userId) {
        return userId != null && Objects.equals(parent.getId(), userId);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
