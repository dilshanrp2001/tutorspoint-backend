package com.tutorspoint.enquiry.domain;

import com.tutorspoint.auth.domain.User;
import com.tutorspoint.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * One message in an enquiry thread. The opening enquiry is the first of these, not a
 * separate thing — which is why a tutor's inbox can render "the last message" without
 * asking whether the thread has been replied to yet.
 *
 * <p>Part of the {@link Enquiry} aggregate: instances are created through
 * {@link Enquiry#open} and {@link Enquiry#reply}, never constructed loose and attached by a
 * caller, which is what guarantees every body on the way in has been through the same
 * contact-detail scrub.
 *
 * <p>The body stored here is the body both sides see. There is no unscrubbed original kept
 * anywhere: a second copy of a message that was deliberately redacted would be a second way
 * to leak it.
 */
@Entity
@Table(name = "enquiry_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnquiryMessage extends BaseEntity {

    /** Long enough for a real first message, short enough that the column is not an essay store. */
    public static final int MAX_BODY_LENGTH = 4000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id", nullable = false, updatable = false)
    private Enquiry enquiry;

    /**
     * Whichever participant wrote it — a {@code User}, not a parent or a tutor, because the
     * thread is read as one ordered list and the reader does not care which table the row
     * came from.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    /** When the other participant read it. Null means unread, which is what a badge counts. */
    @Column(name = "read_at")
    private Instant readAt;

    EnquiryMessage(Enquiry enquiry, User sender, String body) {
        this.enquiry = requireNotNull(enquiry, "enquiry");
        this.sender = requireNotNull(sender, "sender");
        this.body = requireText(body);
    }

    /**
     * When this was sent.
     *
     * <p>Deliberately the audit {@code createdAt} rather than a second timestamp column: a
     * message is sent exactly once, at the moment it is created, and two columns that must
     * always agree are one column and a bug waiting to happen. The name is kept because the
     * domain talks about when a message was <em>sent</em>, not when its row was written.
     */
    public Instant sentAt() {
        return getCreatedAt();
    }

    public boolean isUnread() {
        return readAt == null;
    }

    /** True when somebody other than the sender is looking at it — the only kind that can be read. */
    public boolean isFor(Long viewerId) {
        return viewerId != null && !Objects.equals(sender.getId(), viewerId);
    }

    /**
     * Stamps the moment the recipient saw it. Idempotent: the first read is the one that
     * counts, and re-opening a thread must not keep moving the timestamp forward.
     */
    void markRead(Instant at) {
        if (readAt == null) {
            this.readAt = requireNotNull(at, "at");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("body must be at most " + MAX_BODY_LENGTH + " characters");
        }
        return trimmed;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
