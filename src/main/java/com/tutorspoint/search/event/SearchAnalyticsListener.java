package com.tutorspoint.search.event;

import com.tutorspoint.common.config.TimeConfig;
import com.tutorspoint.search.repository.SearchDailyCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts searches for the metrics summary (OBJ-6).
 *
 * <p>{@code @Async}, off the request thread and outside the search's read-only transaction. A
 * parent waiting for results should not also wait for a counter write, and a counter that
 * failed must not be able to fail a search. A failure here surfaces in the log through the async
 * executor's uncaught-exception handler and goes no further: a pilot metric that is occasionally
 * one short is a far smaller cost than a search that errors.
 */
@Component
@RequiredArgsConstructor
public class SearchAnalyticsListener {

    private final SearchDailyCountRepository counts;

    @Async
    @EventListener
    @Transactional
    public void onSearch(TutorSearchPerformedEvent event) {
        counts.increment(event.at().atZone(TimeConfig.PLATFORM_ZONE).toLocalDate());
    }
}
