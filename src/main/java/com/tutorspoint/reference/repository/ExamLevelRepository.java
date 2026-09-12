package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.ExamLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Exam levels with their names, in display order. See {@link SubjectRepository} on the fetch join. */
public interface ExamLevelRepository extends JpaRepository<ExamLevel, Long> {

    @Query("""
            SELECT e FROM ExamLevel e
            LEFT JOIN FETCH e.translations
            WHERE e.active = TRUE
            ORDER BY e.displayOrder ASC
            """)
    List<ExamLevel> findActiveWithTranslations();
}
