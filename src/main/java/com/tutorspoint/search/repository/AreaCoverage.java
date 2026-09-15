package com.tutorspoint.search.repository;

import com.tutorspoint.reference.domain.Area;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * The one definition of "a served area covers a searched area", shared by the area filter and
 * the area facet so that the count beside an area is exactly what choosing it returns.
 */
final class AreaCoverage {

    private AreaCoverage() {
    }

    /** The same area, the searched town's district, or a town of the searched district. */
    static Predicate covers(CriteriaBuilder cb, Path<Area> served, Path<Area> searched) {
        return cb.or(
                cb.equal(served, searched),
                cb.equal(served, searched.get("parent")),
                cb.equal(served.get("parent"), searched));
    }
}
