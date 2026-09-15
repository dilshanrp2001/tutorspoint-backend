package com.tutorspoint.search.ranking;

/** A position in decimal degrees, WGS 84 - the centre point of an area. */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Math.abs(latitude) > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
