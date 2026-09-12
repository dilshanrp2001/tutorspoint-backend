package com.tutorspoint.reference.dto;

/**
 * One selectable reference value, already rendered in the caller's language.
 *
 * <p>The client stores and sends back the {@code code}; {@code name} is for display only.
 * No database id is exposed — the code is the stable identifier, it means the same thing in
 * every environment, and a request body full of codes is readable in a log.
 */
public record ReferenceItemResponse(
        String code,
        String name) {
}
