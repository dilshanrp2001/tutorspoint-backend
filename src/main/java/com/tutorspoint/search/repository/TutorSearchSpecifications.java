package com.tutorspoint.search.repository;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.search.dto.TutorSearchCriteria;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The search filters, one {@link Specification} each (FR-S1, FR-S7).
 *
 * <p><strong>Composed one filter at a time.</strong> Every method returns null for a filter the
 * searcher did not set, and {@link #matching} drops the nulls, so an absent filter contributes
 * nothing to the query - not a {@code 1 = 1}, not a join. Adding a filter is one method and one
 * line in {@link #matching}.
 *
 * <p><strong>Visibility is not a filter.</strong> {@link #visible()} is part of every search and
 * cannot be switched off from the request: a draft, an unpublished or suspended profile, or the
 * profile of a suspended or deleted account never appears, whatever else is asked.
 *
 * <p>The many-valued filters are {@code EXISTS} subqueries rather than joins. A join would
 * repeat a profile once per matching row, which breaks counting and paging, and would collide
 * with the join a facet count groups by.
 *
 * <p>Criteria API lives here, in the repository layer, and nowhere above it.
 */
public final class TutorSearchSpecifications {

    private static final String ID = "id";
    private static final String CODE = "code";

    private TutorSearchSpecifications() {
    }

    /** Every filter in the criteria, plus visibility. */
    public static Specification<TutorProfile> matching(TutorSearchCriteria criteria) {
        return matching(criteria, null);
    }

    /**
     * As {@link #matching(TutorSearchCriteria)}, leaving out the filter a facet is counted over.
     * Counting subjects under a subject filter would report every other subject as zero.
     */
    public static Specification<TutorProfile> matching(TutorSearchCriteria criteria, FacetDimension ignored) {
        List<Specification<TutorProfile>> filters = new ArrayList<>();
        filters.add(visible());
        filters.add(ignored == FacetDimension.SUBJECT ? null : teachesReference("subjects", criteria.subject()));
        filters.add(ignored == FacetDimension.EXAM_LEVEL ? null : teachesReference("examLevels", criteria.examLevel()));
        filters.add(ignored == FacetDimension.SYLLABUS ? null : teachesReference("syllabuses", criteria.syllabus()));
        filters.add(ignored == FacetDimension.MEDIUM ? null : teachesIn(criteria.medium()));
        filters.add(ignored == FacetDimension.CLASS_FORMAT ? null : teachesAs(criteria.classFormat()));
        filters.add(ignored == FacetDimension.AREA ? null : serves(criteria.area()));
        filters.add(feeReaches(criteria.minFee()));
        filters.add(feeStartsBy(criteria.maxFee()));
        filters.add(criteria.onlineOnly() ? teachesOnline() : null);
        filters.add(criteria.verifiedOnlyRequested() ? verified() : null);
        filters.add(ratedAtLeast(criteria.minRating()));
        filters.add(mentions(criteria.keyword()));
        return Specification.allOf(filters.stream().filter(Objects::nonNull).toList());
    }

    /** Published, and owned by an account that is still active. */
    public static Specification<TutorProfile> visible() {
        return (profile, query, cb) -> cb.and(
                cb.equal(profile.get("status"), ProfileStatus.PUBLISHED),
                cb.equal(profile.get("tutor").get("status"), AccountStatus.ACTIVE));
    }

    public static Specification<TutorProfile> idIn(Collection<Long> profileIds) {
        return (profile, query, cb) -> profile.get(ID).in(profileIds);
    }

    public static Specification<TutorProfile> idNotIn(Collection<Long> profileIds) {
        return (profile, query, cb) -> cb.not(profile.get(ID).in(profileIds));
    }

    /** A subject, exam level or syllabus, by code. */
    static Specification<TutorProfile> teachesReference(String attribute, String code) {
        if (code == null) {
            return null;
        }
        return (profile, query, cb) -> {
            Subquery<Long> taught = query.subquery(Long.class);
            Root<TutorProfile> correlated = taught.correlate(profile);
            Join<TutorProfile, ?> reference = correlated.join(attribute);
            return cb.exists(taught.select(correlated.get(ID))
                    .where(cb.equal(reference.get(CODE), code)));
        };
    }

    static Specification<TutorProfile> teachesIn(Medium medium) {
        if (medium == null) {
            return null;
        }
        return (profile, query, cb) -> cb.isMember(medium, profile.<Collection<Medium>>get("mediums"));
    }

    static Specification<TutorProfile> teachesAs(ClassFormat classFormat) {
        if (classFormat == null) {
            return null;
        }
        return (profile, query, cb) -> cb.isMember(classFormat, profile.<Collection<ClassFormat>>get("classFormats"));
    }

    /**
     * Teaches in person somewhere covering the chosen area.
     *
     * <p>Areas are a two-level tree, and a tutor may list either level, so "covers" goes both
     * ways: a search for a district finds tutors who listed any of its towns, and a search for a
     * town finds tutors who listed the whole district. Siblings do not cover each other - a
     * tutor in Maharagama is not offering Nugegoda just because both are in Colombo.
     */
    static Specification<TutorProfile> serves(String areaCode) {
        if (areaCode == null) {
            return null;
        }
        return (profile, query, cb) -> {
            Subquery<Long> covering = query.subquery(Long.class);
            Root<TutorProfile> correlated = covering.correlate(profile);
            Join<TutorProfile, Area> served = correlated.join("areasServed");
            Root<Area> searched = covering.from(Area.class);
            return cb.exists(covering.select(correlated.get(ID)).where(
                    cb.equal(searched.get(CODE), areaCode),
                    AreaCoverage.covers(cb, served, searched)));
        };
    }

    /** The tutor's range reaches up to the band: their top fee is at least its bottom. */
    static Specification<TutorProfile> feeReaches(BigDecimal minFee) {
        if (minFee == null) {
            return null;
        }
        return (profile, query, cb) -> cb.greaterThanOrEqualTo(profile.get("feeMax"), minFee);
    }

    /** The tutor's range starts within the band: their bottom fee is at most its top. */
    static Specification<TutorProfile> feeStartsBy(BigDecimal maxFee) {
        if (maxFee == null) {
            return null;
        }
        return (profile, query, cb) -> cb.lessThanOrEqualTo(profile.get("feeMin"), maxFee);
    }

    static Specification<TutorProfile> teachesOnline() {
        return (profile, query, cb) -> cb.isTrue(profile.get("availableOnline"));
    }

    static Specification<TutorProfile> verified() {
        return (profile, query, cb) -> cb.isTrue(profile.get("verified"));
    }

    /** An unreviewed tutor has no rating, and so does not clear any minimum - even zero. */
    static Specification<TutorProfile> ratedAtLeast(BigDecimal minRating) {
        if (minRating == null) {
            return null;
        }
        return (profile, query, cb) -> cb.greaterThanOrEqualTo(profile.get("averageRating"), minRating);
    }

    /**
     * Keyword search over the trigger-maintained document (FR-S7). Combined with the structured
     * filters by {@link #matching}, never instead of them. The parsing, the prefix matching and
     * the "no words at all means no filter" rule are in the database function, so that the same
     * rule serves the ranking expression too.
     */
    static Specification<TutorProfile> mentions(String keyword) {
        if (keyword == null) {
            return null;
        }
        return (profile, query, cb) -> cb.isTrue(cb.function(SearchFunctions.MATCHES, Boolean.class,
                profile.get(SearchFunctions.DOCUMENT_ATTRIBUTE), cb.literal(keyword)));
    }
}
