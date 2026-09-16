package com.tutorspoint.common.audit;

import com.tutorspoint.admin.event.AccountModeratedEvent;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.enquiry.event.EnquiryRespondedEvent;
import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogListenerTest {

    @Mock
    private AuditLogRepository auditLogs;

    @Mock
    private RequestOrigin requestOrigin;

    @InjectMocks
    private AuditLogListener listener;

    @Test
    @DisplayName("an auditable event becomes one row, stamped with the client address")
    void writesTheEntry() {
        when(requestOrigin.clientAddress()).thenReturn(Optional.of("203.94.1.7"));

        listener.record(new AccountModeratedEvent(1L, 42L, AccountStatus.ACTIVE, AccountStatus.SUSPENDED, " Spam "));

        AuditLog row = saved();
        assertThat(row.getActorId()).isEqualTo(1L);
        assertThat(row.getAction()).isEqualTo(AuditAction.ACCOUNT_SUSPENDED);
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.USER);
        assertThat(row.getTargetId()).isEqualTo(42L);
        assertThat(row.getBeforeValue()).containsEntry("status", "ACTIVE");
        assertThat(row.getAfterValue()).containsEntry("status", "SUSPENDED");
        assertThat(row.getReason()).isEqualTo("Spam");
        assertThat(row.getIpAddress()).isEqualTo("203.94.1.7");
    }

    @Test
    @DisplayName("the tutor's first reply is recorded as the contact reveal, with the tutor as actor")
    void theRevealIsAudited() {
        when(requestOrigin.clientAddress()).thenReturn(Optional.empty());

        listener.record(new EnquiryRespondedEvent(900L, 5L, "p@example.lk", "Niluka", Language.EN, 7L, "Kasun"));

        AuditLog row = saved();
        assertThat(row.getActorId()).isEqualTo(7L);
        assertThat(row.getAction()).isEqualTo(AuditAction.CONTACT_REVEALED);
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.ENQUIRY);
        assertThat(row.getTargetId()).isEqualTo(900L);
        assertThat(row.getAfterValue()).containsEntry("contactRevealed", true).containsEntry("parentId", 5L);
        // No contact detail is copied into the audit trail, only who could now see whose.
        assertThat(row.getAfterValue().toString()).doesNotContain("p@example.lk");
        assertThat(row.getIpAddress()).isNull();
    }

    @Test
    @DisplayName("snapshot values are reduced to plain JSON: instants and enums as text, nulls kept")
    void snapshotValuesArePlain() {
        var values = AuditEntry.values("at", Instant.parse("2026-09-16T00:00:00Z"), "status", AccountStatus.ACTIVE,
                "verifiedAt", null);

        assertThat(values).containsEntry("at", "2026-09-16T00:00:00Z")
                .containsEntry("status", "ACTIVE")
                .containsEntry("verifiedAt", null);
        assertThatIllegalArgumentException().isThrownBy(() -> AuditEntry.values("dangling"));
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> row = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(row.capture());
        return row.getValue();
    }
}
