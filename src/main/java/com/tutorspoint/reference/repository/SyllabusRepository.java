package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
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
    List<Syllabus> findByCodeInAndActiveTrue(Collection<String> codes);
}
