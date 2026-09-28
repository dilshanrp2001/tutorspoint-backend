package com.tutorspoint.reference.domain;

import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Objects;

/**
 * One reference value's name in one language.
 *
 * <p>A value object, not an entity: a translation has no identity or lifecycle of its own —
 * it exists only as part of the {@link ReferenceEntity} that owns it, and "Mathematics in
 * English" is fully described by its language and its text. Mapped as an
 * {@code @ElementCollection}, so the owning entity's table name decides where the rows live
 * and deleting the owner deletes them.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Translation {

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    private Language language;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    Translation(Language language, String name) {
        if (language == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.language = language;
        this.name = name.trim();
    }

    /**
     * Identity is the language alone, so a set of translations cannot hold two names for the
     * same language — the uniqueness rule is the collection's own, not a check some caller
     * has to remember. It matches the composite primary key on the collection table.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Translation that && language == that.language;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(language);
    }

    @Override
    public String toString() {
        return "%s=%s".formatted(language.name().toLowerCase(Locale.ROOT), name);
    }
}
