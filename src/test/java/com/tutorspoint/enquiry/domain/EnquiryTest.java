package com.tutorspoint.enquiry.domain;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The enquiry lifecycle, and the rule the whole feature exists for: contact details become
 * visible when the tutor replies, and not one moment earlier.
 *
 * <p>Tested on the entity rather than through the service because that is where the rule
 * lives. If {@link Enquiry#contactRevealed()} can be made true without the tutor having
 * written something, no amount of care in a service or a DTO will keep a phone number in.
 */
class EnquiryTest {

    private static final Long PARENT_ID = 1L;
    private static final Long TUTOR_ID = 2L;
    private static final Long STRANGER_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private final Parent parent = parent();
    private final Tutor tutor = tutor();

    @Test
    @DisplayName("a new enquiry is SENT, masked, and already carries the parent's message")
    void opensAsSentAndMasked() {
        Enquiry enquiry = open("Do you teach A/L Chemistry on weekends?");

        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.SENT);
        assertThat(enquiry.contactRevealed()).isFalse();
        assertThat(enquiry.getFirstResponseAt()).isNull();
        assertThat(enquiry.getMessages()).hasSize(1);
        assertThat(enquiry.firstMessage()).get()
                .extracting(EnquiryMessage::getBody)
                .isEqualTo("Do you teach A/L Chemistry on weekends?");
        assertThat(enquiry.firstMessage()).get()
                .extracting(message -> message.getSender().getId())
                .isEqualTo(PARENT_ID);
    }

    @Test
    @DisplayName("the parent writing again does not reveal anything")
    void aParentCannotRevealContactDetailsByReplyingToThemselves() {
        Enquiry enquiry = open("Hello?");

        enquiry.reply(parent, "Are you there?", NOW);

        assertThat(enquiry.contactRevealed()).isFalse();
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.SENT);
        assertThat(enquiry.getFirstResponseAt()).isNull();
    }

    @Test
    @DisplayName("the tutor's first reply moves the thread to RESPONDED and opens the channel")
    void theTutorsFirstReplyRevealsContactDetails() {
        Enquiry enquiry = open("Hello?");

        enquiry.reply(tutor, "Yes, Saturdays are free", NOW);

        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.RESPONDED);
        assertThat(enquiry.getFirstResponseAt()).isEqualTo(NOW);
        assertThat(enquiry.contactRevealed()).isTrue();
    }

    @Test
    @DisplayName("the response moment is the first reply, not the most recent one")
    void theResponseMomentIsNotMovedByLaterMessages() {
        Enquiry enquiry = open("Hello?");
        enquiry.reply(tutor, "Yes", NOW);

        enquiry.reply(tutor, "Shall we say four o'clock?", NOW.plusSeconds(3600));

        assertThat(enquiry.getFirstResponseAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("only the tutor's view moves SENT to VIEWED, and never backwards")
    void viewingIsOneWay() {
        Enquiry enquiry = open("Hello?");

        enquiry.markViewed();
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.VIEWED);

        enquiry.reply(tutor, "Yes", NOW);
        enquiry.markViewed();
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.RESPONDED);
    }

    @Test
    @DisplayName("a stranger is not a participant, and cannot write to the thread")
    void strangersAreRefused() {
        Enquiry enquiry = open("Hello?");
        Tutor stranger = tutor();
        ReflectionTestUtils.setField(stranger, "id", STRANGER_ID);

        assertThat(enquiry.isParticipant(STRANGER_ID)).isFalse();
        assertThat(enquiry.isParticipant(PARENT_ID)).isTrue();
        assertThat(enquiry.isParticipant(TUTOR_ID)).isTrue();
        assertThat(enquiry.isParticipant(null)).isFalse();

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> enquiry.reply(stranger, "Hello", NOW))
                .withMessageContaining("not a participant");
    }

    @Test
    @DisplayName("the counterpart is the other participant, whichever end you ask from")
    void theCounterpartIsTheOtherSide() {
        Enquiry enquiry = open("Hello?");

        assertThat(enquiry.counterpartOf(PARENT_ID)).isSameAs(tutor);
        assertThat(enquiry.counterpartOf(TUTOR_ID)).isSameAs(parent);
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> enquiry.counterpartOf(STRANGER_ID));
    }

    @Test
    @DisplayName("a closed thread takes no further messages")
    void closingEndsTheConversation() {
        Enquiry enquiry = open("Hello?");
        enquiry.reply(tutor, "Yes", NOW);

        enquiry.close(parent);

        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.CLOSED);
        assertThat(enquiry.isOpen()).isFalse();
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> enquiry.reply(tutor, "One more thing", NOW))
                .withMessageContaining("takes no further messages");
    }

    @Test
    @DisplayName("closing a thread twice is not an error, but a spam thread stays spam")
    void closingIsIdempotentAndSpamIsTerminal() {
        Enquiry closable = open("Hello?");
        closable.close(parent);
        closable.close(tutor);
        assertThat(closable.getStatus()).isEqualTo(EnquiryStatus.CLOSED);

        Enquiry spam = open("Buy cheap watches");
        spam.markAsSpam();
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> spam.close(parent));
        assertThat(spam.getStatus()).isEqualTo(EnquiryStatus.SPAM);
    }

    @Test
    @DisplayName("closing a thread the tutor never answered leaves it masked")
    void closingDoesNotReveal() {
        Enquiry enquiry = open("Hello?");

        enquiry.close(parent);

        assertThat(enquiry.contactRevealed()).isFalse();
    }

    @Test
    @DisplayName("unread means unread by the other side, and reading is stamped once")
    void unreadCountsOnlyTheOtherSidesMessages() {
        Enquiry enquiry = open("Hello?");
        enquiry.reply(tutor, "Yes", NOW);
        enquiry.reply(tutor, "Saturdays suit me", NOW.plusSeconds(60));

        // The parent has two messages waiting; their own opening message is not one of them.
        assertThat(enquiry.unreadCountFor(PARENT_ID)).isEqualTo(2);
        assertThat(enquiry.unreadCountFor(TUTOR_ID)).isEqualTo(1);

        assertThat(enquiry.markMessagesRead(PARENT_ID, NOW.plusSeconds(120))).isEqualTo(2);
        assertThat(enquiry.unreadCountFor(PARENT_ID)).isZero();
        // Re-reading changes nothing and does not move the timestamps forward.
        assertThat(enquiry.markMessagesRead(PARENT_ID, NOW.plusSeconds(600))).isZero();
        assertThat(enquiry.getMessages().get(1).getReadAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    @DisplayName("an enquiry names an area or online, never both and never neither")
    void thePlaceIsExactlyOneAnswer() {
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> Enquiry.open(parent, tutor, null, subject(), examLevel(),
                        ClassFormat.ONLINE, area(), true, "Hello"))
                .withMessageContaining("not both and not neither");

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> Enquiry.open(parent, tutor, null, subject(), examLevel(),
                        ClassFormat.ONE_TO_ONE, null, false, "Hello"));

        assertThat(Enquiry.open(parent, tutor, null, subject(), examLevel(),
                ClassFormat.ONLINE, null, true, "Hello").isOnline()).isTrue();
    }

    @Test
    @DisplayName("an enquiry cannot carry somebody else's child")
    void theChildMustBelongToTheParent() {
        Parent otherParent = parent();
        ReflectionTestUtils.setField(otherParent, "id", STRANGER_ID);
        ChildProfile theirChild = otherParent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null);

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> Enquiry.open(parent, tutor, theirChild, subject(), examLevel(),
                        ClassFormat.ONE_TO_ONE, area(), false, "Hello"))
                .withMessageContaining("does not belong to");
    }

    @Test
    @DisplayName("a blank message is not an enquiry")
    void theOpeningMessageMustSaySomething() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> open("   "));
    }

    private Enquiry open(String body) {
        return Enquiry.open(parent, tutor, null, subject(), examLevel(),
                ClassFormat.ONE_TO_ONE, area(), false, body);
    }

    private static Parent parent() {
        Parent parent = new Parent("niluka@example.lk", "hash", "Niluka", "+94770000001", Language.EN);
        ReflectionTestUtils.setField(parent, "id", PARENT_ID);
        return parent;
    }

    private static Tutor tutor() {
        Tutor tutor = new Tutor("kasun@example.lk", "hash", "Kasun", "+94770000002", Language.SI);
        ReflectionTestUtils.setField(tutor, "id", TUTOR_ID);
        return tutor;
    }

    private static Subject subject() {
        return new Subject("CHEMISTRY", 1);
    }

    private static ExamLevel examLevel() {
        return new ExamLevel("GCE_AL", 1);
    }

    private static Area area() {
        return Area.district("COLOMBO", 1, new BigDecimal("6.9271"), new BigDecimal("79.8612"));
    }
}
