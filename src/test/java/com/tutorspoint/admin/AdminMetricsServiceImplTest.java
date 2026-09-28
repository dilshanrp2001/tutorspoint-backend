package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminMetricsResponse;
import com.tutorspoint.admin.dto.MetricsWindow;
import com.tutorspoint.admin.repository.AdminUserRepository;
import com.tutorspoint.admin.repository.AdminUserRepository.RoleCount;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.repository.EnquiryRepository;
import com.tutorspoint.search.repository.SearchDailyCountRepository;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metrics arithmetic and, above all, the window: calendar days in Sri Lanka, which is
 * UTC+05:30, turned into instants. A window that is off by that half-day would move a whole
 * evening of enquiries into the wrong day and nobody would notice by reading the numbers.
 */
@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceImplTest {

    /** 20:00 UTC on the 15th is already 01:30 on the 16th in Colombo. */
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");

    @Mock
    private AdminUserRepository users;

    @Mock
    private TutorProfileRepository profiles;

    @Mock
    private EnquiryRepository enquiries;

    @Mock
    private SearchDailyCountRepository searches;

    @Mock
    private VerificationDocumentRepository documents;

    private AdminMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminMetricsServiceImpl(users, profiles, enquiries, searches, documents,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(users.countRegistrationsByRole(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a window of days becomes Colombo midnight to the midnight after its last day")
    void theWindowIsInColomboTime() {
        service.summary(new MetricsWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)));

        Instant start = Instant.parse("2026-08-31T18:30:00Z");
        Instant end = Instant.parse("2026-09-07T18:30:00Z");
        verify(users).countRegistrationsByRole(start, end);
        verify(enquiries).countSent(start, end, EnquiryStatus.SPAM);
        verify(enquiries).countResponded(start, end, EnquiryStatus.SPAM);
        verify(searches).sumBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));
    }

    @Test
    @DisplayName("with no dates, the window runs from the start to today - today in Colombo, not in UTC")
    void noDatesMeansEverythingUpToToday() {
        AdminMetricsResponse response = service.summary(new MetricsWindow(null, null));

        assertThat(response.from()).isNull();
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 9, 16));
        verify(searches).sumBetween(any(), eq(LocalDate.of(2026, 9, 16)));
    }

    @Test
    @DisplayName("the figures are assembled as counted, and the rate is responded over sent")
    void assemblesTheFigures() {
        when(users.countRegistrationsByRole(any(), any()))
                .thenReturn(List.of(roleCount(Role.TUTOR, 12), roleCount(Role.PARENT, 30), roleCount(Role.STUDENT, 5),
                        roleCount(Role.ADMIN, 1)));
        when(enquiries.countSent(any(), any(), eq(EnquiryStatus.SPAM))).thenReturn(40L);
        when(enquiries.countResponded(any(), any(), eq(EnquiryStatus.SPAM))).thenReturn(30L);
        when(searches.sumBetween(any(), any())).thenReturn(512L);
        when(profiles.countByStatus(ProfileStatus.PUBLISHED)).thenReturn(18L);
        when(profiles.countByStatusAndVerifiedTrue(ProfileStatus.PUBLISHED)).thenReturn(9L);
        when(documents.countByStatus(DocumentStatus.PENDING)).thenReturn(4L);

        AdminMetricsResponse response = service.summary(new MetricsWindow(null, null));

        // Admins are provisioned, not registered.
        assertThat(response.registrations()).isEqualTo(new AdminMetricsResponse.Registrations(12, 30, 5, 47));
        assertThat(response.enquiriesSent()).isEqualTo(40);
        assertThat(response.enquiriesResponded()).isEqualTo(30);
        assertThat(response.responseRate()).isEqualTo(0.75);
        assertThat(response.searches()).isEqualTo(512);
        assertThat(response.publishedProfiles()).isEqualTo(18);
        assertThat(response.verifiedProfiles()).isEqualTo(9);
        assertThat(response.documentsAwaitingReview()).isEqualTo(4);
    }

    @Test
    @DisplayName("with nothing sent there is no rate, rather than a zero that reads as failure")
    void noEnquiriesMeansNoRate() {
        assertThat(service.summary(new MetricsWindow(null, null)).responseRate()).isNull();
    }

    private static RoleCount roleCount(Role role, long total) {
        return new RoleCount() {
            @Override
            public Role getRole() {
                return role;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
