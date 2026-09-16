package com.tutorspoint.enquiry.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts what the pilot is judged on: enquiries sent, enquiries answered, and therefore the
 * enquiry-to-response rate that is objective OBJ-6.
 *
 * <p>A second listener rather than two more lines in the notification one. They consume the
 * same events for unrelated reasons and fail for unrelated reasons, and a metrics registry
 * that threw must not be able to stop a tutor being told they have an enquiry. Separated,
 * each is the whole of one concern and neither can take the other down.
 *
 * <p>Micrometer counters, through the actuator already on the classpath, rather than a table
 * of our own. For a pilot the question is "how many, and what is the ratio", which a counter
 * answers; a durable per-enquiry analytics record is a Phase 5 decision and would be built
 * from the enquiry rows themselves, which are the real system of record either way.
 *
 * <p>Bound to {@code AFTER_COMMIT} like the notifications: a rolled-back enquiry is not an
 * enquiry, and counting one would overstate the numerator of the metric the pilot is read on.
 */
@Slf4j
@Component
public class EnquiryAnalyticsListener {

    static final String SENT_COUNTER = "tutorspoint.enquiries.sent";
    static final String RESPONDED_COUNTER = "tutorspoint.enquiries.responded";

    private final Counter sent;
    private final Counter responded;

    public EnquiryAnalyticsListener(MeterRegistry meters) {
        // Registered at construction rather than looked up per event, so both counters exist
        // and read zero from startup. A metric that only appears once something has happened
        // cannot be told apart from a metric that is broken.
        this.sent = Counter.builder(SENT_COUNTER)
                .description("Enquiries opened by parents (FR-E1)")
                .register(meters);
        this.responded = Counter.builder(RESPONDED_COUNTER)
                .description("Enquiries answered by the tutor, counted on the first reply only (OBJ-6)")
                .register(meters);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnquiryCreated(EnquiryCreatedEvent event) {
        sent.increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnquiryResponded(EnquiryRespondedEvent event) {
        responded.increment();
    }
}
