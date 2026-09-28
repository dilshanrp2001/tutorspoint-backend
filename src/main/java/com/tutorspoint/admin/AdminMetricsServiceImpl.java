package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminMetricsResponse;
import com.tutorspoint.admin.dto.AdminMetricsResponse.Registrations;
import com.tutorspoint.admin.dto.MetricsWindow;
import com.tutorspoint.admin.repository.AdminUserRepository;
import com.tutorspoint.admin.repository.AdminUserRepository.RoleCount;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.common.config.TimeConfig;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.repository.EnquiryRepository;
import com.tutorspoint.search.repository.SearchDailyCountRepository;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Counts the pilot is judged on, read from the tables that are the system of record.
 *
 * <p>Not from the Micrometer counters in {@code EnquiryAnalyticsListener}. Those restart at zero
 * with the process and cannot answer "last week"; the enquiries table can, and it is the same
 * data the counters were counting.
 *
 * <p>A window is calendar days in {@link TimeConfig#PLATFORM_ZONE}, turned here into the
 * half-open instant range {@code [start of from, start of the day after to)} the queries take.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsServiceImpl implements AdminMetricsService {

    /** "From the start" for the search counter, which has no rows before the platform existed. */
    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    private final AdminUserRepository users;
    private final TutorProfileRepository profiles;
    private final EnquiryRepository enquiries;
    private final SearchDailyCountRepository searches;
    private final VerificationDocumentRepository documents;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminMetricsResponse summary(MetricsWindow window) {
        LocalDate to = window.to() != null ? window.to() : LocalDate.now(clock.withZone(TimeConfig.PLATFORM_ZONE));
        LocalDate from = window.from();
        Instant start = (from != null ? from : BEGINNING).atStartOfDay(TimeConfig.PLATFORM_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(TimeConfig.PLATFORM_ZONE).toInstant();

        long sent = enquiries.countSent(start, end, EnquiryStatus.SPAM);
        long responded = enquiries.countResponded(start, end, EnquiryStatus.SPAM);

        return new AdminMetricsResponse(
                from,
                to,
                registrations(users.countRegistrationsByRole(start, end)),
                searches.sumBetween(from != null ? from : BEGINNING, to),
                sent,
                responded,
                sent == 0 ? null : (double) responded / sent,
                profiles.countByStatus(ProfileStatus.PUBLISHED),
                profiles.countByStatusAndVerifiedTrue(ProfileStatus.PUBLISHED),
                documents.countByStatus(DocumentStatus.PENDING));
    }

    private static Registrations registrations(List<RoleCount> counts) {
        long tutors = countFor(counts, Role.TUTOR);
        long parents = countFor(counts, Role.PARENT);
        long students = countFor(counts, Role.STUDENT);
        return new Registrations(tutors, parents, students, tutors + parents + students);
    }

    private static long countFor(List<RoleCount> counts, Role role) {
        return counts.stream().filter(count -> count.getRole() == role).mapToLong(RoleCount::getTotal).sum();
    }
}
