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
 * What a student is working towards — Grade 5 Scholarship through to a professional
 * qualification. The single most useful search filter, because it narrows both the subject
 * list and the kind of tutor worth showing.
 */
@Entity
@Table(name = "exam_levels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamLevel extends ReferenceEntity {

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exam_level_translations", joinColumns = @JoinColumn(name = "exam_level_id"))
    private Set<Translation> translations = new LinkedHashSet<>();

    public ExamLevel(String code, int displayOrder) {
        super(code, displayOrder);
    }

    @Override
    protected Set<Translation> translations() {
        return translations;
    }
}
