package com.tutorspoint.reference.domain;

import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What every reference value has in common: a stable code, a place in a dropdown, an
 * on/off switch, and a name in each of the three languages.
 *
 * <p><strong>The code, not the id, is the identifier the rest of the world uses.</strong>
 * It is what the API exposes, what a tutor profile and a search filter are stored against,
 * and what a seed migration joins on. A generated id is an implementation detail that
 * differs between a developer's database and production; a code does not.
 *
 * <p>Values are retired with {@link #deactivate()} rather than deleted. A subject that is
 * no longer offered is still referenced by last year's profiles and enquiries, and a
 * dangling foreign key is a worse outcome than a row nobody picks from a dropdown.
 */
@MappedSuperclass
@Getter
public abstract class ReferenceEntity extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    /** Ascending. Reference lists are ordered by meaning — O/L before A/L — not alphabetically. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ReferenceEntity() {
    }

    protected ReferenceEntity(String code, int displayOrder) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.displayOrder = displayOrder;
        this.active = true;
    }

    /** The owning entity's translation collection. Mapped per subclass, so each gets its own table. */
    protected abstract Set<Translation> translations();

    /**
     * This value's name for a caller, falling back to English and finally to the code.
     *
     * <p>The fallback is the reason this lives on the entity: a missing Tamil name must
     * degrade to something readable rather than to a null in a dropdown, and that decision
     * should not be re-made by every caller that renders reference data.
     */
    public String nameIn(Language language) {
        return find(language)
                .or(() -> find(Language.EN))
                .orElse(code);
    }

    public Set<Translation> getTranslations() {
        return Collections.unmodifiableSet(translations());
    }

    /**
     * Adds or replaces this value's name in one language.
     *
     * @throws IllegalArgumentException if the language or name is missing
     */
    public void translate(Language language, String name) {
        Translation translation = new Translation(language, name);
        // Equality is by language, so removing first makes this an upsert rather than a
        // silent no-op when a name is being corrected.
        translations().remove(translation);
        translations().add(translation);
    }

    /** Brings a retired value back into the dropdowns. */
    public void activate() {
        this.active = true;
    }

    /** Retires the value: existing references keep working, new ones cannot be made. */
    public void deactivate() {
        this.active = false;
    }

    private Optional<String> find(Language language) {
        return translations().stream()
                .filter(translation -> translation.getLanguage() == language)
                .map(Translation::getName)
                .findFirst();
    }

    @Override
    public String toString() {
        return "%s(%s)".formatted(getClass().getSimpleName(), code);
    }
}
