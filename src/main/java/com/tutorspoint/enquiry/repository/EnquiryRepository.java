package com.tutorspoint.enquiry.repository;

import com.tutorspoint.enquiry.domain.Enquiry;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Enquiry threads, read from whichever end the caller is standing at.
 *
 * <p>Every finder is scoped to a participant. There is no {@code findById} used by the
 * service: a thread is reached only through a query that already filters by the caller, so
 * somebody else's enquiry id behaves exactly like one that does not exist and the endpoint
 * cannot be used to probe for threads.
 *
 * <p>The entity graphs cover the to-one associations only. Joining the message collection
 * as well would make every paged query fetch its rows and paginate them in memory; the
 * messages come instead from the {@code default_batch_fetch_size=50} configured in
 * {@code application.yml}, which loads the whole page's threads in one further statement.
 */
public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    /** One thread the caller is a participant in. Anybody else gets an empty result. */
    @EntityGraph(attributePaths = {"parent", "tutor", "childProfile", "subject", "examLevel", "preferredArea"})
    Optional<Enquiry> findByIdAndParentId(Long id, Long parentId);

    @EntityGraph(attributePaths = {"parent", "tutor", "childProfile", "subject", "examLevel", "preferredArea"})
    Optional<Enquiry> findByIdAndTutorId(Long id, Long tutorId);

    /** A parent's sent enquiries (FR-P2), newest first. */
    @EntityGraph(attributePaths = {"parent", "tutor", "subject", "examLevel", "preferredArea"})
    Page<Enquiry> findByParentIdOrderByCreatedAtDesc(Long parentId, Pageable pageable);

    @EntityGraph(attributePaths = {"parent", "tutor", "subject", "examLevel", "preferredArea"})
    Page<Enquiry> findByParentIdAndStatusOrderByCreatedAtDesc(Long parentId, EnquiryStatus status, Pageable pageable);

    /** A tutor's received enquiries, newest first. */
    @EntityGraph(attributePaths = {"parent", "tutor", "subject", "examLevel", "preferredArea"})
    Page<Enquiry> findByTutorIdOrderByCreatedAtDesc(Long tutorId, Pageable pageable);

    @EntityGraph(attributePaths = {"parent", "tutor", "subject", "examLevel", "preferredArea"})
    Page<Enquiry> findByTutorIdAndStatusOrderByCreatedAtDesc(Long tutorId, EnquiryStatus status, Pageable pageable);

    /**
     * How many enquiries this parent has opened since a moment — the hourly cap that stops
     * one account spraying every tutor in a district (FR-E1).
     */
    long countByParentIdAndCreatedAtAfter(Long parentId, Instant since);

    /**
     * Whether a live thread already exists between these two. A second enquiry to a tutor
     * who has not answered the first is not a new question, it is a nudge, and it belongs in
     * the existing thread. The partial unique index in V8 is the backstop for two requests
     * arriving at once; this is the readable error.
     */
    boolean existsByParentIdAndTutorIdAndStatusIn(Long parentId, Long tutorId, Collection<EnquiryStatus> statuses);

    /**
     * Enquiries sent in {@code [from, to)}, leaving out threads moderated as spam - the
     * denominator of the response rate (OBJ-6). Spam is neither a real enquiry nor one a tutor
     * should be measured on answering; see {@link EnquiryStatus#SPAM}.
     */
    @Query("""
            select count(e) from Enquiry e
            where e.createdAt >= :from and e.createdAt < :to and e.status <> :excluded
            """)
    long countSent(@Param("from") Instant from, @Param("to") Instant to,
                   @Param("excluded") EnquiryStatus excluded);

    /**
     * Of those, the ones the tutor has answered. Keyed on the enquiry's send date, not the reply
     * date, so the two counts describe the same set of enquiries and their ratio is a rate.
     */
    @Query("""
            select count(e) from Enquiry e
            where e.createdAt >= :from and e.createdAt < :to and e.status <> :excluded
              and e.firstResponseAt is not null
            """)
    long countResponded(@Param("from") Instant from, @Param("to") Instant to,
                        @Param("excluded") EnquiryStatus excluded);

    /**
     * Messages waiting for this parent across every thread they have opened: what the header
     * badge shows. Counted in the database rather than summed from an inbox page, because a
     * page is bounded and a reply on an old thread must still be noticed.
     *
     * <p>The same rule as {@code Enquiry.unreadCountFor}: unread, and written by somebody else.
     */
    @Query("""
            select count(m) from EnquiryMessage m
            where m.enquiry.parent.id = :parentId and m.sender.id <> :parentId and m.readAt is null
            """)
    long countUnreadForParent(@Param("parentId") Long parentId);

    /** The tutor's side of the same count. */
    @Query("""
            select count(m) from EnquiryMessage m
            where m.enquiry.tutor.id = :tutorId and m.sender.id <> :tutorId and m.readAt is null
            """)
    long countUnreadForTutor(@Param("tutorId") Long tutorId);
}
