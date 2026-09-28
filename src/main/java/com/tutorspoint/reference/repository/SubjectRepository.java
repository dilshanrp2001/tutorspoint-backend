package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * The subject list behind every dropdown and search filter.
 *
 * <p>The fetch join is the point: a subject is useless without its three names, and
 * without it rendering the list would issue one query per subject. Reading the whole
 * active list in one statement is also what makes the result worth caching.
 */
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @Query("""
            SELECT s FROM Subject s
            LEFT JOIN FETCH s.translations
            WHERE s.active = TRUE
            ORDER BY s.displayOrder ASC
            """)
    List<Subject> findActiveWithTranslations();

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
    List<Subject> findByCodeInAndActiveTrue(Collection<String> codes);
}
