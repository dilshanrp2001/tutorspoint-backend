package com.tutorspoint.search.domain;

import com.tutorspoint.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * How many searches were run on one day (OBJ-6).
 *
 * <p>Mapped so the counts can be read through a repository; never constructed or changed in
 * Java. Incrementing is an atomic upsert in {@code SearchDailyCountRepository}, because a
 * read-modify-write here would lose counts to every pair of concurrent searches.
 *
 * <p>A count and nothing else, deliberately: no search terms, no areas, no account. See
 * {@code V9__audit_log_and_metrics.sql} on why the platform does not keep what people searched for.
 */
@Entity
@Table(name = "search_daily_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchDailyCount extends BaseEntity {

    /** The calendar day in {@code TimeConfig.PLATFORM_ZONE}. */
    @Column(name = "day", nullable = false, updatable = false)
    private LocalDate day;

    @Column(name = "search_count", nullable = false)
    private long searchCount;
}
