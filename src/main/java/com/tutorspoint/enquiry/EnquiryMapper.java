package com.tutorspoint.enquiry;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.enquiry.domain.Enquiry;
import com.tutorspoint.enquiry.domain.EnquiryMessage;
import com.tutorspoint.enquiry.domain.Shortlist;
import com.tutorspoint.enquiry.dto.ContactDetailsDto;
import com.tutorspoint.enquiry.dto.EnquiryChildDto;
import com.tutorspoint.enquiry.dto.EnquiryDetailResponse;
import com.tutorspoint.enquiry.dto.EnquiryMessageDto;
import com.tutorspoint.enquiry.dto.EnquirySummaryResponse;
import com.tutorspoint.enquiry.dto.ShortlistResponse;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.tutor.TutorMapper;
import com.tutorspoint.tutor.domain.TutorProfile;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

/**
 * The one place an enquiry becomes a response DTO.
 *
 * <p>Two contexts travel with every call, exactly as they do in {@code TutorMapper}:
 * {@link Language} is the caller's language, and {@link ReferenceLabels} translates the two
 * enum-backed values that have no translation rows of their own.
 *
 * <p>{@code viewerId} is the third thing every enquiry mapping needs, and it is a plain
 * parameter rather than a context because it changes what the DTO says: an unread count is
 * unread <em>to somebody</em>, and the same thread renders differently to each of its two
 * participants. Making it an argument is what stops a caller silently getting the other
 * side's view.
 *
 * <p><strong>Masking is not decided here.</strong> This mapper receives the contact details it
 * is to render, or null, and puts them where they go. Whether they were allowed out is
 * {@code Enquiry.contactRevealed()} answered in the service, which is also where the reveal is
 * logged — a mapper that decided it would be a second place the rule lived, and the one nobody
 * would think to audit.
 *
 * <p>Reference values reuse {@code TutorMapper}'s item mappings rather than restating them, and
 * a shortlisted tutor reuses its card, so a tutor looks the same in a shortlist as in the
 * search results they were saved from.
 *
 * <p>Generated rather than hand-written, and the build sets {@code unmappedTargetPolicy=ERROR}:
 * a field added to any of these DTOs is a compile failure until it is mapped, not a null that
 * reaches a client.
 */
@Mapper(uses = TutorMapper.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface EnquiryMapper {

    /** Characters of the last message shown in an inbox row. One line on a phone. */
    int PREVIEW_LENGTH = 140;

    @Mapping(target = "id", source = "enquiry.id")
    @Mapping(target = "tutorId", source = "enquiry.tutor.id")
    @Mapping(target = "tutorName", source = "enquiry.tutor.fullName")
    @Mapping(target = "seekerId", source = "enquiry.seeker.id")
    @Mapping(target = "seekerName", source = "enquiry.seeker.fullName")
    @Mapping(target = "subject", source = "enquiry.subject")
    @Mapping(target = "examLevel", source = "enquiry.examLevel")
    @Mapping(target = "preferredFormat",
            expression = "java(labels.classFormat(enquiry.getPreferredFormat(), language))")
    @Mapping(target = "preferredArea", source = "enquiry.preferredArea")
    @Mapping(target = "online", source = "enquiry.online")
    @Mapping(target = "status", source = "enquiry.status")
    @Mapping(target = "createdAt", source = "enquiry.createdAt")
    @Mapping(target = "firstResponseAt", source = "enquiry.firstResponseAt")
    @Mapping(target = "lastMessagePreview", expression = "java(preview(enquiry))")
    @Mapping(target = "lastMessageAt", expression = "java(lastMessageAt(enquiry))")
    @Mapping(target = "unreadCount", expression = "java(enquiry.unreadCountFor(viewerId))")
    EnquirySummaryResponse toSummary(Enquiry enquiry,
                                     Long viewerId,
                                     @Context Language language,
                                     @Context ReferenceLabels labels);

    /**
     * The whole thread.
     *
     * @param contact the other participant's details, or null while the thread is still
     *                masked. Decided by the service, never here.
     */
    @Mapping(target = "id", source = "enquiry.id")
    @Mapping(target = "tutorId", source = "enquiry.tutor.id")
    @Mapping(target = "tutorName", source = "enquiry.tutor.fullName")
    @Mapping(target = "seekerId", source = "enquiry.seeker.id")
    @Mapping(target = "seekerName", source = "enquiry.seeker.fullName")
    @Mapping(target = "child", source = "enquiry.childProfile")
    @Mapping(target = "subject", source = "enquiry.subject")
    @Mapping(target = "examLevel", source = "enquiry.examLevel")
    @Mapping(target = "preferredFormat",
            expression = "java(labels.classFormat(enquiry.getPreferredFormat(), language))")
    @Mapping(target = "preferredArea", source = "enquiry.preferredArea")
    @Mapping(target = "online", source = "enquiry.online")
    @Mapping(target = "status", source = "enquiry.status")
    @Mapping(target = "createdAt", source = "enquiry.createdAt")
    @Mapping(target = "firstResponseAt", source = "enquiry.firstResponseAt")
    @Mapping(target = "contactRevealed", expression = "java(enquiry.contactRevealed())")
    @Mapping(target = "contact", source = "contact")
    @Mapping(target = "messages", source = "enquiry.messages")
    EnquiryDetailResponse toDetail(Enquiry enquiry,
                                   ContactDetailsDto contact,
                                   @Context Language language,
                                   @Context ReferenceLabels labels);

    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", source = "sender.fullName")
    @Mapping(target = "sentAt", expression = "java(message.sentAt())")
    EnquiryMessageDto toMessageDto(EnquiryMessage message);

    EnquiryChildDto toChildDto(ChildProfile child);

    /**
     * A participant's contact details.
     *
     * <p>Calling this is the reveal. It is deliberately the narrowest possible mapping —
     * three fields, no account state, no verification flags — so that the thing which crosses
     * the masking boundary is small enough to read in full.
     */
    ContactDetailsDto toContact(User user);

    /**
     * A saved tutor and their public card.
     *
     * @param profile the tutor's published profile, or null if they have taken it down — the
     *                entry still comes back, so the seeker can see what became of a tutor they
     *                saved
     */
    @Mapping(target = "id", source = "shortlist.id")
    @Mapping(target = "tutorId", source = "shortlist.tutor.id")
    @Mapping(target = "tutorName", source = "shortlist.tutor.fullName")
    @Mapping(target = "tutor", source = "profile")
    @Mapping(target = "note", source = "shortlist.note")
    @Mapping(target = "savedAt", source = "shortlist.createdAt")
    ShortlistResponse toShortlistResponse(Shortlist shortlist,
                                          TutorProfile profile,
                                          @Context Language language,
                                          @Context ReferenceLabels labels);

    /**
     * The one-line preview an inbox row shows: the newest message, flattened and cut short.
     *
     * <p>Newlines collapse to spaces because a message that opens with three blank lines would
     * otherwise render as an empty row, and the reader would have to open the thread to find
     * out whether anything was said.
     */
    default String preview(Enquiry enquiry) {
        return enquiry.lastMessage()
                .map(EnquiryMessage::getBody)
                .map(body -> body.replaceAll("\\s+", " ").trim())
                .map(body -> body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "…")
                .orElse(null);
    }

    default Instant lastMessageAt(Enquiry enquiry) {
        return enquiry.lastMessage().map(EnquiryMessage::sentAt).orElse(null);
    }
}
