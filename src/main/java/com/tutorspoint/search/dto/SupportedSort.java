package com.tutorspoint.search.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value names a registered ranking strategy, or is absent.
 *
 * <p>Checked against the strategies that actually exist rather than a hardcoded list, so a new
 * ordering is accepted the moment its class is, with no edit here.
 */
@Documented
@Constraint(validatedBy = SupportedSortValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedSort {

    String message() default "{validation.search.sort.unsupported}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
