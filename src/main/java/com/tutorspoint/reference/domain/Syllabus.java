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
 * The curriculum a class follows — national (by medium), Cambridge, Edexcel, or a
 * professional body's.
 *
 * <p>A distinct axis from {@link ExamLevel} and from {@code Medium}: a Cambridge O/L and a
 * national O/L are the same level and different syllabuses, and a parent who wants one
 * will not accept the other.
 */
@Entity
@Table(name = "syllabuses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Syllabus extends ReferenceEntity {

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "syllabus_translations", joinColumns = @JoinColumn(name = "syllabus_id"))
    private Set<Translation> translations = new LinkedHashSet<>();

    public Syllabus(String code, int displayOrder) {
        super(code, displayOrder);
    }

    @Override
    protected Set<Translation> translations() {
        return translations;
    }
}
