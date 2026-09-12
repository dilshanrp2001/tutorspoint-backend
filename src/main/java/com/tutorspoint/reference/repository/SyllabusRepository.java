package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Syllabuses with their names, in display order. See {@link SubjectRepository} on the fetch join. */
public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {

    @Query("""
            SELECT s FROM Syllabus s
            LEFT JOIN FETCH s.translations
            WHERE s.active = TRUE
            ORDER BY s.displayOrder ASC
            """)
    List<Syllabus> findActiveWithTranslations();
}
