package com.tutorspoint.reference.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A district and the towns inside it, nested so the client can render a grouped area
 * picker from a single response instead of one request per district.
 */
public record DistrictResponse(
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        List<TownResponse> towns) {
}
