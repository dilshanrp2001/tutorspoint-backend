package com.tutorspoint.reference.repository;

import com.tutorspoint.reference.domain.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Areas, read as the two-level tree the UI shows rather than as a flat table.
 *
 * <p>The whole tree — districts, their towns, and the names of both — comes back in one
 * statement. It is a small cartesian product (districts x names x towns x names) and it is
 * read once per language and then cached, which is the trade worth making against the
 * dozens of round trips a lazy walk would cost.
 *
 * <p>Restricting a fetched collection to its active rows leaves the loaded {@code towns}
 * set deliberately incomplete. That is safe here and only here: these instances are read
 * inside a read-only transaction and turned into DTOs, never modified and never written
 * back, so there is no partial collection to flush.
 */
public interface AreaRepository extends JpaRepository<Area, Long> {

    @Query("""
            SELECT district FROM Area district
            LEFT JOIN FETCH district.translations
            LEFT JOIN FETCH district.towns town
            LEFT JOIN FETCH town.translations
            WHERE district.active = TRUE
              AND district.type = com.tutorspoint.reference.domain.AreaType.DISTRICT
              AND (town IS NULL OR town.active = TRUE)
            ORDER BY district.displayOrder ASC
            """)
    List<Area> findActiveDistrictsWithTowns();

    Optional<Area> findByCode(String code);

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
    List<Area> findByCodeInAndActiveTrue(Collection<String> codes);

    /** One active area by code - the home base a tutor teaches from. */
    Optional<Area> findByCodeAndActiveTrue(String code);
}
