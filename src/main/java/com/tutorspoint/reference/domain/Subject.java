package com.tutorspoint.reference.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Something a tutor teaches and a parent searches for — "Combined Mathematics",
 * "Chemistry", "Spoken English".
 *
 * <p>The list is the real Sri Lankan one, seeded by migration, and it is deliberately flat:
 * subjects that differ between exam levels are genuinely different subjects (O/L
 * Mathematics and A/L Combined Mathematics are not the same thing), so the level is not an
 * attribute of the subject. {@code display_order} groups them the way a syllabus does.
 */
@Entity
@Table(name = "subjects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject extends ReferenceEntity {

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "subject_translations", joinColumns = @JoinColumn(name = "subject_id"))
    private Set<Translation> translations = new LinkedHashSet<>();

    public Subject(String code, int displayOrder) {
        super(code, displayOrder);
    }

    @Override
    protected Set<Translation> translations() {
        return translations;
    }
}
