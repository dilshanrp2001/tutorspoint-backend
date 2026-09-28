package com.tutorspoint.common.audit;

/**
 * A domain event whose occurrence must be on the audit record (NFR-10).
 *
 * <p>This is how auditing stays out of the services. A service publishes the event it would
 * publish anyway — a document was reviewed, a tutor answered an enquiry — and the event says
 * what it means for the audit trail. {@link AuditLogListener} records every one of them; no
 * service calls an audit method, and none can forget to.
 */
public interface AuditableEvent {

    AuditEntry auditEntry();
}
