package com.tutorspoint.search.dto;

import com.tutorspoint.search.ranking.RankingStrategyFactory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/**
 * Asks the factory rather than keeping a list of its own. Spring builds constraint validators
 * through the application context, which is what lets this one be constructor-injected.
 */
@RequiredArgsConstructor
public class SupportedSortValidator implements ConstraintValidator<SupportedSort, String> {

    private final RankingStrategyFactory strategies;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return strategies.supports(value);
    }
}
