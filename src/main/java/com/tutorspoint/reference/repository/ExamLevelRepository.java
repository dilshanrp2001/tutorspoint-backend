package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.ExamLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
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

    /**
     * The active values behind a set of codes, for resolving what a tutor selected in the
     * profile wizard.
     *
     * <p>Active only, deliberately: a retired value may stay on the profiles that already
     * reference it, but nothing new may be built from one. The caller compares the size of the
     * result against the size of the request to find out which codes were rejected - a query
     * that returns four rows for five codes has said which is unknown, without a round trip
     * per code.
     */
    List<ExamLevel> findByCodeInAndActiveTrue(Collection<String> codes);
}
