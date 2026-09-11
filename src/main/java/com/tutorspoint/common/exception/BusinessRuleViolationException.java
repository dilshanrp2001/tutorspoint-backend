package com.tutorspoint.common.exception;

/**
 * A domain rule forbids the requested action in the current state
 * (duplicate review, enquiry already closed, profile not publishable). Maps to 409.
 */
public class BusinessRuleViolationException extends TutorsPointException {

    private static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleViolationException(String message) {
        super(CODE, message);
    }

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
