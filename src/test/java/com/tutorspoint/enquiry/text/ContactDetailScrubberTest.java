package com.tutorspoint.enquiry.text;

import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scrubber, against the two things it has to get right in opposite directions: it must
 * catch a Sri Lankan phone number however it was typed, and it must leave ordinary sentences
 * alone.
 *
 * <p>The second half matters as much as the first. A scrubber that eats fees, years and
 * reference numbers teaches parents that the message box mangles what they write, and a parent
 * who does not trust the message box takes the conversation somewhere else — which is the
 * outcome the masking rule exists to prevent.
 *
 * <p>A real {@link StaticMessageSource} rather than a mock: the notice is translated text, and
 * a test that asserted against a stubbed string would not notice the day the key stopped
 * resolving.
 */
class ContactDetailScrubberTest {

    private static final String PHONE_NOTICE = "[phone removed]";
    private static final String EMAIL_NOTICE = "[email removed]";

    private final ContactDetailScrubber scrubber = new ContactDetailScrubber(messages());

    @ParameterizedTest(name = "removes {0}")
    @DisplayName("every Sri Lankan phone format people actually type is removed")
    @ValueSource(strings = {
            "0771234567",
            "077 123 4567",
            "077-123-4567",
            "077.123.4567",
            "+94771234567",
            "+94 77 123 4567",
            "+94 77-123-4567",
            "0094771234567",
            "94771234567",
            // A landline, which is the same shape with a different prefix.
            "0112345678",
            "011 234 5678"
    })
    void removesSriLankanPhoneNumbers(String number) {
        String scrubbed = scrubber.scrub("Call me on " + number + " tonight", Language.EN);

        assertThat(scrubbed)
                .contains(PHONE_NOTICE)
                .doesNotContain("1234567", "2345678")
                // The sentence around it survives: the parent's actual question is still there.
                .startsWith("Call me on ")
                .endsWith(" tonight");
    }

    @ParameterizedTest(name = "removes {0}")
    @DisplayName("email addresses are removed, including the ones written to dodge a filter")
    @ValueSource(strings = {
            "kasun@gmail.com",
            "kasun.perera@yahoo.co.uk",
            "kasun+tuition@gmail.com",
            "kasun (at) gmail.com",
            "kasun [at] gmail.com",
            "kasun at gmail dot com"
    })
    void removesEmailAddresses(String address) {
        String scrubbed = scrubber.scrub("Write to " + address + " please", Language.EN);

        assertThat(scrubbed)
                .contains(EMAIL_NOTICE)
                .doesNotContain("gmail", "yahoo")
                .startsWith("Write to ")
                .endsWith(" please");
    }

    @Test
    @DisplayName("several contact details in one message are all removed")
    void removesEveryOccurrence() {
        String scrubbed = scrubber.scrub(
                "Reach me on 0771234567 or 0119876543, or kasun@gmail.com", Language.EN);

        assertThat(scrubbed).doesNotContain("0771234567", "0119876543", "kasun@gmail.com");
        assertThat(scrubbed.split(java.util.regex.Pattern.quote(PHONE_NOTICE), -1)).hasSize(3);
    }

    @ParameterizedTest(name = "leaves {0} alone")
    @DisplayName("ordinary numbers in an ordinary message are left alone")
    @ValueSource(strings = {
            "My daughter is in Grade 11 and sat the exam in 2024",
            "Is 2500 per hour negotiable?",
            "We can do 3 classes a week, 90 minutes each",
            "She scored 85 in her last term test",
            "I am free between 4 and 6 on weekdays",
            "Meet at 10.30 on Saturday"
    })
    void leavesOrdinaryTextAlone(String message) {
        assertThat(scrubber.scrub(message, Language.EN)).isEqualTo(message);
        assertThat(scrubber.containsContactDetails(message)).isFalse();
    }

    @Test
    @DisplayName("a message with nothing to remove comes back identical")
    void untouchedWhenThereIsNothingToRemove() {
        String message = "Hello, do you teach A/L Chemistry in Nugegoda on weekends?";

        assertThat(scrubber.scrub(message, Language.EN)).isSameAs(message);
    }

    @Test
    @DisplayName("null and blank bodies are handled rather than crashed on")
    void toleratesNothingToScrub() {
        assertThat(scrubber.scrub(null, Language.EN)).isNull();
        assertThat(scrubber.scrub("   ", Language.EN)).isEqualTo("   ");
        assertThat(scrubber.containsContactDetails(null)).isFalse();
    }

    @Test
    @DisplayName("the notice is rendered in the writer's own language")
    void theNoticeIsTranslated() {
        assertThat(scrubber.scrub("Call 0771234567", Language.SI)).contains("[සිංහල]");
    }

    private static StaticMessageSource messages() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage(ContactDetailScrubber.PHONE_NOTICE_KEY, Locale.of("en"), PHONE_NOTICE);
        source.addMessage(ContactDetailScrubber.EMAIL_NOTICE_KEY, Locale.of("en"), EMAIL_NOTICE);
        source.addMessage(ContactDetailScrubber.PHONE_NOTICE_KEY, Locale.of("si"), "[සිංහල]");
        source.addMessage(ContactDetailScrubber.EMAIL_NOTICE_KEY, Locale.of("si"), "[සිංහල]");
        return source;
    }
}
