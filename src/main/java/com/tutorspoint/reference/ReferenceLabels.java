package com.tutorspoint.reference;

import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Turns the reference enums into the same {@code {code, name}} shape the tables produce, so
 * a client filling a dropdown cannot tell which of the two a value came from.
 *
 * <p>Not part of {@link ReferenceMapper}: nothing here maps an entity to a DTO. It is a
 * message-bundle lookup, which needs a {@link MessageSource} — a collaborator MapStruct
 * would have to field-inject into a generated class. Translation still has exactly one
 * home; this is simply a different kind of translation.
 */
@Component
@RequiredArgsConstructor
public class ReferenceLabels {

    private static final String MEDIUM_PREFIX = "reference.medium.";
    private static final String CLASS_FORMAT_PREFIX = "reference.class-format.";

    private final MessageSource messages;

    public List<ReferenceItemResponse> mediums(Language language) {
        return labels(Medium.values(), MEDIUM_PREFIX, language);
    }

    public List<ReferenceItemResponse> classFormats(Language language) {
        return labels(ClassFormat.values(), CLASS_FORMAT_PREFIX, language);
    }

    /**
     * Enum declaration order is the display order: these lists are short, closed and ordered
     * by meaning, so the enum itself is the only place that ordering needs to be stated.
     */
    private <E extends Enum<E>> List<ReferenceItemResponse> labels(E[] values, String keyPrefix, Language language) {
        return Arrays.stream(values)
                .map(value -> new ReferenceItemResponse(value.name(), label(keyPrefix, value, language)))
                .toList();
    }

    private String label(String keyPrefix, Enum<?> value, Language language) {
        // The enum name is the last-resort default, for the same reason a reference row falls
        // back to its code: a dropdown entry with no label is worse than an untranslated one.
        return messages.getMessage(keyPrefix + value.name(), null, value.name(), language.toLocale());
    }
}
