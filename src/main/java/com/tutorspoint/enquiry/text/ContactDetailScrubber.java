package com.tutorspoint.enquiry.text;

import com.tutorspoint.common.domain.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes phone numbers and email addresses from a message body while a thread is still
 * pre-reveal, replacing each with a short notice in the writer's own language.
 *
 * <p>This exists because the masking rule is worth nothing if the first message can say
 * "call me on 077 123 4567". The reveal is deliberate and dated (see
 * {@code Enquiry.contactRevealed()}); a number typed into the body is neither, and it takes
 * the conversation off the platform before the tutor has answered — which is precisely the
 * behaviour objective OBJ-4 exists to prevent.
 *
 * <p><strong>Obvious contact details, not every possible one.</strong> "zero seven seven,
 * one two three" defeats this and always will; the goal is to close the easy path and make
 * the intent visible, not to win an arms race with a determined user. What it must never do
 * is mangle ordinary text — a fee of "2500" and a year like "2024" are left alone, because a
 * scrubber that eats normal sentences is one users learn to work around.
 *
 * <p>Scrubbing happens on the way in and the result is what is stored. There is no
 * unscrubbed original: a second copy of a body that was deliberately redacted would be a
 * second way to leak it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContactDetailScrubber {

    /** Resolved per language, so the notice reads in the language the writer was using. */
    static final String PHONE_NOTICE_KEY = "enquiry.contact-removed.phone";
    static final String EMAIL_NOTICE_KEY = "enquiry.contact-removed.email";

    /**
     * Anything that looks like an email address. Deliberately broad on the local part and
     * strict about needing a dotted domain, which is what keeps "see you @ 4" intact.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\]|\\s+at\\s+)\\s*[A-Za-z0-9.-]+\\s*(?:\\.|\\s+dot\\s+)\\s*[A-Za-z]{2,}",
            Pattern.CASE_INSENSITIVE);

    /**
     * Sri Lankan mobile and landline numbers in the forms people actually type them.
     *
     * <p>Three shapes, in one alternation:
     * <ul>
     *   <li>{@code +94} or {@code 0094} followed by nine digits — the international form;</li>
     *   <li>{@code 0} followed by nine digits — the local form, {@code 0771234567};</li>
     *   <li>{@code 94} followed by nine digits — what people write when they drop the plus.</li>
     * </ul>
     *
     * <p>Separators between the digit groups are allowed and unrestricted in kind, because a
     * number is written {@code 077 123 4567}, {@code 077-123-4567} and {@code 077.123.4567}
     * by different people and all three are the same number.
     *
     * <p>The leading and trailing boundaries are what stop a long reference number or a price
     * from matching: the pattern must start at a non-digit and end at one.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![0-9])(?:\\+\\s?94|0094|94(?=[\\s.-]?[1-9])|0)(?:[\\s.-]?[0-9]){9}(?![0-9])");

    private final MessageSource messages;

    /**
     * The body as it may be stored: contact details replaced by a notice.
     *
     * @param body     the text as written
     * @param language the writer's language, which the notice is rendered in
     * @return the scrubbed body, or the original when it held nothing to remove
     */
    public String scrub(String body, Language language) {
        if (body == null || body.isBlank()) {
            return body;
        }
        // Email first: an address can contain digits that the phone pattern would otherwise
        // bite a piece out of, leaving a half-redacted address behind.
        String scrubbed = replaceAll(body, EMAIL, notice(EMAIL_NOTICE_KEY, language));
        scrubbed = replaceAll(scrubbed, PHONE, notice(PHONE_NOTICE_KEY, language));
        if (!scrubbed.equals(body)) {
            // No body text in the log line. Recording that a scrub happened is useful for
            // spotting a pattern of attempts; recording what was removed would put the
            // contact details straight into the application log.
            log.info("Stripped contact details from an enquiry message");
        }
        return scrubbed;
    }

    /** Whether the body holds anything this class would remove. Used by tests and diagnostics. */
    public boolean containsContactDetails(String body) {
        return body != null && (EMAIL.matcher(body).find() || PHONE.matcher(body).find());
    }

    private String notice(String key, Language language) {
        Locale locale = Locale.of(language.name().toLowerCase(Locale.ROOT));
        return messages.getMessage(key, null, locale);
    }

    private static String replaceAll(String body, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(body);
        // Quoted, because a notice is translated text and a stray $ or backslash in a Sinhala
        // or Tamil string must not be read as a group reference.
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }
}
