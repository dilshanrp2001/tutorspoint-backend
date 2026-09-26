package com.tutorspoint.search.repository;

import com.tutorspoint.search.domain.SearchDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/** The per-day search counter behind the metrics summary. */
public interface SearchDailyCountRepository extends JpaRepository<SearchDailyCount, Long> {

    /**
     * Adds one to a day's count, creating the row on the first search of the day.
     *
     * <p>Native because JPQL has no upsert, and an upsert is the only correct shape: two searches
     * arriving together on a new day must produce one row with a count of two, which a "find, then
     * insert or update" in Java cannot promise. The conflict target is {@code uq_search_daily_counts_day}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO search_daily_counts (day, search_count, created_at, updated_at)
            VALUES (:day, 1, now(), now())
            ON CONFLICT (day) DO UPDATE
                SET search_count = search_daily_counts.search_count + 1,
                    updated_at   = now()
            """, nativeQuery = true)
    void increment(@Param("day") LocalDate day);

    /** Searches from {@code from} to {@code to}, both days inclusive. Zero when there were none. */
    @Query("""
            select coalesce(sum(c.searchCount), 0)
            from SearchDailyCount c
            where c.day >= :from and c.day <= :to
            """)
    long sumBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
