package com.tutorspoint.reference.domain;

/**
 * How far down the area hierarchy a row sits.
 *
 * <p>Stored explicitly rather than inferred from "has a parent", so a query can ask for
 * districts without a correlated subquery, and so adding a level later (province above,
 * suburb below) is a value in this enum plus a data migration — not a rewrite of every
 * query that assumed two levels.
 */
public enum AreaType {

    /** One of Sri Lanka's administrative districts. */
    DISTRICT,

    /** A town or suburb within a district. What a tutor and a parent actually name. */
    TOWN
}
