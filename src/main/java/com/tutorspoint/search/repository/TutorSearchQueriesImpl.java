package com.tutorspoint.search.repository;

import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.search.ranking.GeoPoint;
import com.tutorspoint.search.ranking.RankingContext;
import com.tutorspoint.search.ranking.SortKey;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Criteria queries for search. The only class in the feature that touches an
 * {@link EntityManager}, and it sits in the repository layer where that is allowed.
 */
@RequiredArgsConstructor
class TutorSearchQueriesImpl implements TutorSearchQueries {

    private static final String ID = "id";
    private static final String CODE = "code";

    private final EntityManager entityManager;

    @Override
    public List<Long> findRankedIds(Specification<TutorProfile> matching, List<SortKey> order,
                                    RankingContext context, long offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<TutorProfile> profile = query.from(TutorProfile.class);
        query.select(profile.get(ID));
        where(query, matching.toPredicate(profile, query, cb));

        OrderExpressions expressions = new OrderExpressions(profile, cb, context);
        List<Order> orders = new ArrayList<>();
        for (SortKey key : order) {
            expressions.of(key).ifPresent(expression -> {
                if (key.field().isNullable()) {
                    // Missing values last in both directions. PostgreSQL would put them first
                    // in a descending sort, which would lead "best rated" with the unrated.
                    orders.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(expression), 1).otherwise(0)));
                }
                orders.add(key.descending() ? cb.desc(expression) : cb.asc(expression));
            });
        }
        orders.add(cb.asc(profile.get(ID)));
        query.orderBy(orders);

        return entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(offset))
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Map<String, Long> countByFacet(FacetDimension dimension, Specification<TutorProfile> matching) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<TutorProfile> profile = query.from(TutorProfile.class);

        List<Predicate> predicates = new ArrayList<>();
        Optional.ofNullable(matching.toPredicate(profile, query, cb)).ifPresent(predicates::add);

        Expression<?> value = switch (dimension) {
            case SUBJECT -> profile.join("subjects").get(CODE);
            case EXAM_LEVEL -> profile.join("examLevels").get(CODE);
            case SYLLABUS -> profile.join("syllabuses").get(CODE);
            case MEDIUM -> profile.join("mediums");
            case CLASS_FORMAT -> profile.join("classFormats");
            case AREA -> {
                // Every area a profile covers, not only the ones it lists: a second root over
                // areas, joined through the same coverage rule the area filter uses.
                Join<TutorProfile, Area> served = profile.join("areasServed");
                Root<Area> area = query.from(Area.class);
                predicates.add(cb.isTrue(area.get("active")));
                predicates.add(AreaCoverage.covers(cb, served, area));
                yield area.get(CODE);
            }
        };

        query.multiselect(value, cb.countDistinct(profile))
                .where(predicates.toArray(Predicate[]::new))
                .groupBy(value);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            Object key = row.get(0);
            counts.put(key instanceof Enum<?> constant ? constant.name() : String.valueOf(key), row.get(1, Long.class));
        }
        return counts;
    }

    private static void where(CriteriaQuery<?> query, Predicate predicate) {
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Translates ranking vocabulary into expressions over one query's root. A key that cannot
     * apply to this search - relevance with no keyword, distance with no origin - yields
     * nothing and is skipped, so a strategy never has to guard against it.
     */
    private static final class OrderExpressions {

        private final Root<TutorProfile> profile;
        private final CriteriaBuilder cb;
        private final RankingContext context;
        private Join<TutorProfile, Area> homeBase;

        OrderExpressions(Root<TutorProfile> profile, CriteriaBuilder cb, RankingContext context) {
            this.profile = profile;
            this.cb = cb;
            this.context = context;
        }

        Optional<Expression<?>> of(SortKey key) {
            return switch (key.field()) {
                case KEYWORD_RELEVANCE -> context.hasKeyword()
                        ? Optional.of(cb.function(SearchFunctions.RANK, Double.class,
                        profile.get(SearchFunctions.DOCUMENT_ATTRIBUTE), cb.literal(context.keyword())))
                        : Optional.empty();
                case VERIFIED -> Optional.of(profile.get("verified"));
                case AVAILABILITY -> Optional.of(cb.<Integer>selectCase()
                        .when(cb.equal(profile.get("availabilityStatus"), AvailabilityStatus.ACCEPTING), 0)
                        .when(cb.equal(profile.get("availabilityStatus"), AvailabilityStatus.LIMITED), 1)
                        .otherwise(2));
                case RATING -> Optional.of(profile.get("averageRating"));
                case REVIEW_COUNT -> Optional.of(profile.get("reviewCount"));
                case FEE -> Optional.of(profile.get("feeMin"));
                case EXPERIENCE -> Optional.of(profile.get("yearsOfExperience"));
                case DISTANCE -> context.originPoint().map(this::distanceFrom);
            };
        }

        private Expression<?> distanceFrom(GeoPoint origin) {
            // A left join, and only when distance is asked for: an inner join would silently drop
            // every online-only tutor, who has no home base to measure to.
            if (homeBase == null) {
                homeBase = profile.join("homeBaseArea", JoinType.LEFT);
            }
            return cb.function(SearchFunctions.GREAT_CIRCLE_KM, Double.class,
                    cb.literal(origin.latitude()), cb.literal(origin.longitude()),
                    homeBase.get("latitude"), homeBase.get("longitude"));
        }
    }
}
