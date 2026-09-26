package com.tutorspoint.reference;

import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The table-free half of the reference data.
 *
 * <p>Deliberately wired to the real {@code messages*.properties} rather than a mocked
 * {@link org.springframework.context.MessageSource}: a mock would prove the code calls a
 * message source, which is not in doubt. What is worth proving is that the Sinhala and
 * Tamil entries actually exist — the requirement is trilingual output, and a missing key
 * silently degrades to English in production.
 *
 * <p>The source is configured exactly as Spring Boot configures the application's own
 * (basename, UTF-8, no system-locale fallback), so a mismatch here is a real mismatch.
 */
class ReferenceLabelsTest {

    private final ReferenceLabels labels = new ReferenceLabels(messageSource());

    @Test
    void everyMediumIsNamedInAllThreeLanguages() {
        assertThat(codesOf(labels.mediums(Language.EN)))
                .containsExactly("SINHALA", "TAMIL", "ENGLISH");

        assertThat(namesOf(labels.mediums(Language.EN))).containsExactly("Sinhala", "Tamil", "English");
        assertThat(namesOf(labels.mediums(Language.SI))).containsExactly("සිංහල", "දෙමළ", "ඉංග්‍රීසි");
        assertThat(namesOf(labels.mediums(Language.TA))).containsExactly("சிங்களம்", "தமிழ்", "ஆங்கிலம்");
    }

    @Test
    void everyClassFormatIsNamedInAllThreeLanguages() {
        assertThat(codesOf(labels.classFormats(Language.EN)))
                .containsExactly("ONE_TO_ONE", "SMALL_GROUP", "MASS_CLASS", "ONLINE");

        for (Language language : Language.values()) {
            assertThat(namesOf(labels.classFormats(language)))
                    .as("class formats in %s", language)
                    .hasSize(ClassFormat.values().length)
                    .noneMatch(String::isBlank);
        }
    }

    @Test
    void sinhalaAndTamilAreRealTranslationsRatherThanTheEnglishFallback() {
        // If a key were missing from messages_si.properties, Spring would quietly return the
        // English text and the response would look fine while being wrong.
        assertThat(namesOf(labels.mediums(Language.SI)))
                .doesNotContainAnyElementsOf(namesOf(labels.mediums(Language.EN)));
        assertThat(namesOf(labels.classFormats(Language.TA)))
                .doesNotContainAnyElementsOf(namesOf(labels.classFormats(Language.EN)));
    }

    @Test
    void theListIsOrderedByTheEnumSoTheUiOrderIsDeclaredInOnePlace() {
        assertThat(codesOf(labels.classFormats(Language.SI)))
                .containsExactlyElementsOf(List.of(ClassFormat.values()).stream().map(Enum::name).toList());
        assertThat(codesOf(labels.mediums(Language.TA)))
                .containsExactlyElementsOf(List.of(Medium.values()).stream().map(Enum::name).toList());
    }

    private static List<String> codesOf(List<ReferenceItemResponse> items) {
        return items.stream().map(ReferenceItemResponse::code).toList();
    }

    private static List<String> namesOf(List<ReferenceItemResponse> items) {
        return items.stream().map(ReferenceItemResponse::name).toList();
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
