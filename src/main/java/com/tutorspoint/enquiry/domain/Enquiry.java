package com.tutorspoint.enquiry.domain;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Seeker;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A seeker asking a tutor about teaching them — a parent on behalf of a child, or a student
 * for themselves — and the conversation that follows (FR-E1 - FR-E3). The aggregate root of the enquiry package.
 *
 * <p><strong>The reveal rule lives here.</strong> Contact details become visible when, and
 * only when, the tutor has replied — and "the tutor has replied" is not a flag a caller
 * sets, it is {@link #firstResponseAt}, stamped by {@link #reply} the first time the reply
 * comes from the tutor's side. A service that forgot to set a flag would silently open the
 * channel early; there is no flag to forget. {@link #contactRevealed()} is the same rule
 * read by everything that renders a DTO, so the API and the domain cannot disagree about
 * when a phone number is allowed out.
 *
 * <p>The opening message is the first {@link EnquiryMessage} of the thread rather than a
 * column of its own. One body, one place, one scrub — see {@link EnquiryMessage}.
 *
 * <p>No setters. Each method below refuses what the domain forbids: a closed thread takes
 * no more messages, a seeker replying does not move the thread to RESPONDED, and a thread
 * marked spam is terminal.
 */
@Entity
@Table(name = "enquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enquiry extends BaseEntity {

    /** Stable keys the client switches on and translates into si / ta / en. */
    private static final String ERROR_THREAD_CLOSED = "ENQUIRY_CLOSED";
    private static final String ERROR_NOT_PARTICIPANT = "ENQUIRY_NOT_PARTICIPANT";
    private static final String ERROR_CHILD_NOT_OWNED = "CHILD_PROFILE_NOT_OWNED";
    private static final String ERROR_CHILD_NOT_ALLOWED = "CHILD_PROFILE_NOT_ALLOWED";
    private static final String ERROR_PLACE_REQUIRED = "ENQUIRY_PLACE_REQUIRED";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seeker_id", nullable = false, updatable = false)
    private Seeker seeker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false, updatable = false)
    private Tutor tutor;

    /**
     * Which child this is about (FR-A5). Optional: a student enquiring for themselves has no
     * sub-profile to name, and a parent may have created none.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_profile_id")
    private ChildProfile childProfile;

    /** Reference rows, never text: the tutor's inbox and their profile name the same subject. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false, updatable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_level_id", nullable = false, updatable = false)
    private ExamLevel examLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_format", nullable = false, length = 20)
    private ClassFormat preferredFormat;

    /** Where the seeker wants the classes. Null exactly when {@link #online} is true. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_area_id")
    private Area preferredArea;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnquiryStatus status;

    /**
     * The moment the tutor first answered — and therefore the moment contact details became
     * visible to both participants. A timestamp rather than a boolean because "has the tutor
     * replied" and "when did the channel open" are the same question, and a reveal that
     * cannot be dated cannot be audited (NFR-10).
     */
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    /**
     * The thread, oldest first. Cascaded and orphan-removed because a message has no life
     * outside the enquiry it belongs to.
     */
    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<EnquiryMessage> messages = new ArrayList<>();

    private Enquiry(Seeker seeker,
                    Tutor tutor,
                    ChildProfile childProfile,
                    Subject subject,
                    ExamLevel examLevel,
                    ClassFormat preferredFormat,
                    Area preferredArea,
                    boolean online) {
        this.seeker = required(seeker, "seeker");
        this.tutor = required(tutor, "tutor");
        this.childProfile = childProfile;
        this.subject = required(subject, "subject");
        this.examLevel = required(examLevel, "examLevel");
        this.preferredFormat = required(preferredFormat, "preferredFormat");
        this.preferredArea = preferredArea;
        this.online = online;
        this.status = EnquiryStatus.SENT;
    }

    /**
     * Opens a thread with the seeker's first message.
     *
     * <p>A factory rather than a public constructor because an enquiry is never valid
     * half-built: it exists from the moment it has something to say, and the opening message
     * is created here so it cannot be forgotten by a caller or added twice.
     *
     * @param body the first message, already scrubbed of contact details by the caller —
     *             the entity stores what it is given and keeps no second copy
     * @throws BusinessRuleViolationException if a child is named by a student, or belongs to
     *                                        another parent, or the request names neither an
     *                                        area nor online
     */
    public static Enquiry open(Seeker seeker,
                               Tutor tutor,
                               ChildProfile childProfile,
                               Subject subject,
                               ExamLevel examLevel,
                               ClassFormat preferredFormat,
                               Area preferredArea,
                               boolean online,
                               String body) {
        required(seeker, "seeker");
        // An area or online, exactly one. Checked here rather than in the service because
        // "online in Nugegoda" is not something this entity is ever allowed to represent, no
        // matter who asks.
        if (online == (preferredArea != null)) {
            throw new BusinessRuleViolationException(ERROR_PLACE_REQUIRED,
                    "An enquiry names either a preferred area or online classes, not both and not neither");
        }
        // The entity checks ownership itself rather than trusting the service to have done
        // it: a child's grade and school would otherwise leak into a stranger's thread.
        if (childProfile != null && !(seeker instanceof Parent)) {
            // Children are a Parent concern (FR-A8). A student asks about themselves, and a
            // child id from one is a client bug, not an ownership question.
            throw new BusinessRuleViolationException(ERROR_CHILD_NOT_ALLOWED,
                    "Account %s is not a parent account and cannot name a child profile"
                            .formatted(seeker.getId()));
        }
        if (childProfile != null && !childProfile.belongsTo(seeker.getId())) {
            throw new BusinessRuleViolationException(ERROR_CHILD_NOT_OWNED,
                    "Child profile %s does not belong to account %s"
                            .formatted(childProfile.getId(), seeker.getId()));
        }
        Enquiry enquiry = new Enquiry(seeker, tutor, childProfile, subject, examLevel,
                preferredFormat, preferredArea, online);
        enquiry.messages.add(new EnquiryMessage(enquiry, seeker, body));
        return enquiry;
    }

    /** Read-only: messages are added through {@link #reply}, never by a caller. */
    public List<EnquiryMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Adds a message from one of the participants, and — when it is the tutor's first —
     * stamps the response that opens the contact channel.
     *
     * @return the new message, so a caller can read its id after the flush
     * @throws BusinessRuleViolationException if the sender is not a participant, or the
     *                                        thread is closed or marked spam
     */
    public EnquiryMessage reply(User sender, String body, Instant at) {
        requireParticipant(sender);
        ensureOpen();
        EnquiryMessage message = new EnquiryMessage(this, sender, body);
        messages.add(message);
        if (isTutor(sender) && firstResponseAt == null) {
            this.firstResponseAt = required(at, "at");
            this.status = EnquiryStatus.RESPONDED;
        }
        return message;
    }

    /**
     * The tutor has opened the thread (FR-E1). Only moves SENT to VIEWED: a thread that has
     * already been answered does not go backwards, and a seeker re-reading their own enquiry
     * is not the tutor viewing it.
     */
    public void markViewed() {
        if (status == EnquiryStatus.SENT) {
            this.status = EnquiryStatus.VIEWED;
        }
    }

    /**
     * Marks everything the viewer has not yet seen as read, and answers how many that was.
     *
     * <p>The viewer's own messages are untouched: "read" means the other side read it, which
     * is the only reading an unread badge can be built on.
     */
    public int markMessagesRead(Long viewerId, Instant at) {
        int newlyRead = 0;
        for (EnquiryMessage message : messages) {
            if (message.isFor(viewerId) && message.isUnread()) {
                message.markRead(at);
                newlyRead++;
            }
        }
        return newlyRead;
    }

    /** How many messages the viewer has not read. What the inbox badge shows. */
    public long unreadCountFor(Long viewerId) {
        return messages.stream().filter(message -> message.isFor(viewerId) && message.isUnread()).count();
    }

    /**
     * Either participant considers the conversation finished.
     *
     * <p>Idempotent for a thread that is already closed — closing twice is a double-clicked
     * button, not an error — but a spam thread stays spam.
     *
     * @throws BusinessRuleViolationException if the caller is not a participant, or the
     *                                        thread was marked spam
     */
    public void close(User closedBy) {
        requireParticipant(closedBy);
        if (status == EnquiryStatus.SPAM) {
            throw new BusinessRuleViolationException(ERROR_THREAD_CLOSED,
                    "Enquiry %s has been marked as spam".formatted(getId()));
        }
        this.status = EnquiryStatus.CLOSED;
    }

    /**
     * Moderation (Phase 5): the thread is abuse. Terminal — nothing reopens it, and it is
     * deliberately not CLOSED, which is a normal outcome and counts towards the response rate.
     */
    public void markAsSpam() {
        this.status = EnquiryStatus.SPAM;
    }

    /**
     * Whether contact details may be shown on this thread yet (FR-E2, NFR-5).
     *
     * <p>The single definition of the masking rule. Everything that renders an enquiry asks
     * this rather than re-deriving it from the status, so there is no second, subtly
     * different version of it to get wrong.
     */
    public boolean contactRevealed() {
        return firstResponseAt != null;
    }

    /** Whether this thread will accept another message. */
    public boolean isOpen() {
        return status != EnquiryStatus.CLOSED && status != EnquiryStatus.SPAM;
    }

    public boolean isParticipant(Long userId) {
        return userId != null
                && (Objects.equals(seeker.getId(), userId) || Objects.equals(tutor.getId(), userId));
    }

    /** The first message of the thread — the enquiry itself, as the seeker wrote it. */
    public Optional<EnquiryMessage> firstMessage() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }

    /** The most recent message, for the one-line preview an inbox list shows. */
    public Optional<EnquiryMessage> lastMessage() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(messages.size() - 1));
    }

    /**
     * The participant who is not the given one — whose contact details a reveal hands over.
     *
     * @throws BusinessRuleViolationException if the given account is not in this thread
     */
    public User counterpartOf(Long viewerId) {
        requireParticipant(viewerId);
        return Objects.equals(seeker.getId(), viewerId) ? tutor : seeker;
    }

    private boolean isTutor(User user) {
        return Objects.equals(tutor.getId(), user.getId());
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new BusinessRuleViolationException(ERROR_THREAD_CLOSED,
                    "Enquiry %s is %s and takes no further messages".formatted(getId(), status));
        }
    }

    private void requireParticipant(User user) {
        requireParticipant(required(user, "user").getId());
    }

    private void requireParticipant(Long userId) {
        if (!isParticipant(userId)) {
            throw new BusinessRuleViolationException(ERROR_NOT_PARTICIPANT,
                    "Account %s is not a participant in enquiry %s".formatted(userId, getId()));
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
