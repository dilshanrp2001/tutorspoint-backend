package com.tutorspoint.search.event;

import com.tutorspoint.search.repository.SearchDailyCountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchAnalyticsListenerTest {

    @Mock
    private SearchDailyCountRepository counts;

    @InjectMocks
    private SearchAnalyticsListener listener;

    @Test
    @DisplayName("a search is counted on the day it was in Sri Lanka, not in UTC")
    void countsOnTheColomboDay() {
        // 19:00 UTC on the 15th is 00:30 on the 16th in Colombo.
        listener.onSearch(new TutorSearchPerformedEvent(Instant.parse("2026-09-15T19:00:00Z")));

        verify(counts).increment(LocalDate.of(2026, 9, 16));
    }
}
