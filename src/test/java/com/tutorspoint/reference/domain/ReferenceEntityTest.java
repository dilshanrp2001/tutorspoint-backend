package com.tutorspoint.reference.domain;

import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The name-resolution rule every reference value shares, exercised through {@link Subject}
 * because the superclass is abstract and the behaviour is identical for all four types.
 *
 * <p>The fallback chain is the whole point of putting this on the entity: a dropdown must
 * never render a null or an empty string, however incomplete the translation table is.
 */
class ReferenceEntityTest {

    @Test
    void returnsTheNameInTheRequestedLanguage() {
        Subject subject = fullyTranslated();

        assertThat(subject.nameIn(Language.EN)).isEqualTo("Combined Mathematics");
        assertThat(subject.nameIn(Language.SI)).isEqualTo("සංයුක්ත ගණිතය");
        assertThat(subject.nameIn(Language.TA)).isEqualTo("இணைந்த கணிதம்");
    }

    @Test
    void fallsBackToEnglishWhenTheRequestedLanguageIsMissing() {
        Subject subject = new Subject("COMBINED_MATHEMATICS", 400);
        subject.translate(Language.EN, "Combined Mathematics");

        assertThat(subject.nameIn(Language.TA)).isEqualTo("Combined Mathematics");
    }

    @Test
    void fallsBackToTheCodeWhenEvenEnglishIsMissing() {
        Subject subject = new Subject("COMBINED_MATHEMATICS", 400);

        // Unreachable through the seeded data, and that is exactly why it is worth pinning:
        // the guarantee is that a caller gets a renderable string, not that the data is good.
        assertThat(subject.nameIn(Language.EN)).isEqualTo("COMBINED_MATHEMATICS");
        assertThat(subject.nameIn(Language.SI)).isEqualTo("COMBINED_MATHEMATICS");
    }

    @Test
    void translatingTheSameLanguageTwiceCorrectsItRatherThanDuplicatingIt() {
        Subject subject = new Subject("ICT", 220);
        subject.translate(Language.EN, "IT");
        subject.translate(Language.EN, "Information & Communication Technology");

        assertThat(subject.getTranslations()).hasSize(1);
        assertThat(subject.nameIn(Language.EN)).isEqualTo("Information & Communication Technology");
    }

    @Test
    void codeIsNormalisedSoLookupsAndSeedsCannotDisagreeOnCase() {
        assertThat(new Subject(" combined_mathematics ", 400).getCode()).isEqualTo("COMBINED_MATHEMATICS");
    }

    @Test
    void rejectsAValueWithNoCode() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Subject("  ", 1));
    }

    @Test
    void rejectsABlankTranslation() {
        Subject subject = new Subject("ICT", 220);

        assertThatIllegalArgumentException().isThrownBy(() -> subject.translate(Language.SI, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> subject.translate(null, "නම"));
    }

    @Test
    void isActiveOnCreationAndRetiresWithoutBeingDeleted() {
        Subject subject = fullyTranslated();
        assertThat(subject.isActive()).isTrue();

        subject.deactivate();
        assertThat(subject.isActive()).isFalse();
        // Still fully readable: last year's profiles still point at it.
        assertThat(subject.nameIn(Language.SI)).isEqualTo("සංයුක්ත ගණිතය");

        subject.activate();
        assertThat(subject.isActive()).isTrue();
    }

    @Test
    void translationsAreNotMutableThroughTheGetter() {
        Subject subject = fullyTranslated();

        assertThat(subject.getTranslations()).isUnmodifiable();
    }

    private static Subject fullyTranslated() {
        Subject subject = new Subject("COMBINED_MATHEMATICS", 400);
        subject.translate(Language.EN, "Combined Mathematics");
        subject.translate(Language.SI, "සංයුක්ත ගණිතය");
        subject.translate(Language.TA, "இணைந்த கணிதம்");
        return subject;
    }
}
