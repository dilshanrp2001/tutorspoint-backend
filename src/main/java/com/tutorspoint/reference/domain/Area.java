package com.tutorspoint.reference.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where teaching happens: a district, or a town inside one.
 *
 * <p>Hierarchical and self-referencing, so the pilot's three districts and the rest of the
 * country are the same shape — adding Kandy is a data migration, never a code change. A
 * district is created with {@link #district}, and towns only through
 * {@link #addTown(String, int, BigDecimal, BigDecimal)} on their district, so a town
 * cannot exist without one.
 *
 * <p>Every area carries a centre point. Nothing sorts by distance yet (Phase 3 does), but
 * coordinates that are collected from the start are coordinates nobody has to backfill for
 * a table that search depends on.
 */
@Entity
@Table(name = "areas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Area extends ReferenceEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AreaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Area parent;

    @OneToMany(mappedBy = "parent")
    @OrderBy("displayOrder ASC")
    private Set<Area> towns = new LinkedHashSet<>();

    /** Decimal degrees, WGS 84. Six decimal places resolves to roughly a tenth of a metre. */
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "area_translations", joinColumns = @JoinColumn(name = "area_id"))
    private Set<Translation> translations = new LinkedHashSet<>();

    private Area(String code, int displayOrder, AreaType type, Area parent,
                 BigDecimal latitude, BigDecimal longitude) {
        super(code, displayOrder);
        this.type = type;
        this.parent = parent;
        this.latitude = requireLatitude(latitude);
        this.longitude = requireLongitude(longitude);
    }

    /** Creates a top-level district. */
    public static Area district(String code, int displayOrder, BigDecimal latitude, BigDecimal longitude) {
        return new Area(code, displayOrder, AreaType.DISTRICT, null, latitude, longitude);
    }

    /**
     * Adds a town to this district and returns it.
     *
     * @throws IllegalStateException if called on a town — the hierarchy is two levels deep,
     *                               and the entity enforces that rather than trusting the caller
     */
    public Area addTown(String code, int displayOrder, BigDecimal latitude, BigDecimal longitude) {
        if (type != AreaType.DISTRICT) {
            throw new IllegalStateException("Only a district can hold towns; %s is a %s".formatted(getCode(), type));
        }
        Area town = new Area(code, displayOrder, AreaType.TOWN, this, latitude, longitude);
        towns.add(town);
        return town;
    }

    /** Read-only: towns are added through {@link #addTown}, never by a caller mutating the set. */
    public Set<Area> getTowns() {
        return Collections.unmodifiableSet(towns);
    }

    public boolean isDistrict() {
        return type == AreaType.DISTRICT;
    }

    @Override
    protected Set<Translation> translations() {
        return translations;
    }

    private static BigDecimal requireLatitude(BigDecimal value) {
        return requireDegrees(value, 90, "latitude");
    }

    private static BigDecimal requireLongitude(BigDecimal value) {
        return requireDegrees(value, 180, "longitude");
    }

    private static BigDecimal requireDegrees(BigDecimal value, int limit, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new IllegalArgumentException("%s must be between -%d and %d, was %s".formatted(field, limit, limit, value));
        }
        return value;
    }
}
