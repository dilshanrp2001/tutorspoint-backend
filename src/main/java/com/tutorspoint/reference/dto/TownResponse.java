package com.tutorspoint.reference.dto;

import java.math.BigDecimal;

/** A town or suburb, with the centre point that later powers "tutors near me". */
public record TownResponse(
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude) {
}
