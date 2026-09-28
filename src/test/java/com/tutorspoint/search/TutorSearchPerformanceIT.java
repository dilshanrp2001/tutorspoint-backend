package com.tutorspoint.search;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.tutor.seed.DemoTutorSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-1: search results return within two seconds. Held to a quarter of that here, against the
 * 500 demo tutors the dev profile seeds, through the full HTTP stack - binding, validation,
 * every query including the six facet counts, mapping and JSON.
 *
 * <p>A quarter, because the budget belongs to the whole round trip on a mobile network and the
 * server's share has to be small. And each search is timed after a warm-up and judged on its
 * slowest repetition, so a pass means "consistently fast", not "fast once".
 *
 * <p>Seeded inside the test transaction and rolled back, like every other integration test.
 */
@Transactional
class TutorSearchPerformanceIT extends AbstractIntegrationTest {

    private static final Duration NFR_1_BUDGET = Duration.ofSeconds(2);
    private static final Duration SERVER_SHARE = NFR_1_BUDGET.dividedBy(4);
    private static final int REPETITIONS = 5;

    @Autowired
    private DemoTutorSeeder seeder;

    @Autowired
    private TutorProfileRepository profiles;

    @Test
    @DisplayName("filtered searches over 500 seeded tutors complete well inside the two-second budget")
    void searchStaysWithinBudget() throws Exception {
        int published = seeder.seed(500, 20_260_915L);
        profiles.flush();
        assertThat(published).as("most demo tutors are published").isBetween(420, 490);
        assertThat(seeder.alreadySeeded()).isTrue();

        Map<String, MockHttpServletRequestBuilder> searches = new LinkedHashMap<>();
        searches.put("A/L in Colombo by rating", get("/api/search/tutors")
                .param("examLevel", "GCE_AL").param("area", "COLOMBO").param("sort", "rating_desc"));
        searches.put("Sinhala-medium maths in a price band", get("/api/search/tutors")
                .param("subject", "MATHEMATICS").param("medium", "SINHALA").param("minFee", "1000").param("maxFee", "5000"));
        searches.put("keyword, verified only", get("/api/search/tutors")
                .param("keyword", "chem").param("verifiedOnly", "true"));
        searches.put("nearest small groups to Nugegoda", get("/api/search/tutors")
                .param("area", "COLOMBO_NUGEGODA").param("classFormat", "SMALL_GROUP").param("sort", "distance_asc"));
        searches.put("unfiltered, deep page", get("/api/search/tutors").param("page", "10").param("size", "20"));

        // Warm-up: JIT, Hibernate's query plan cache, PostgreSQL's plans.
        for (MockHttpServletRequestBuilder search : searches.values()) {
            mockMvc.perform(search).andExpect(status().isOk());
        }

        Map<String, Duration> slowest = new LinkedHashMap<>();
        for (Map.Entry<String, MockHttpServletRequestBuilder> search : searches.entrySet()) {
            Duration worst = Duration.ZERO;
            for (int i = 0; i < REPETITIONS; i++) {
                long started = System.nanoTime();
                String body = mockMvc.perform(search.getValue())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                Duration took = Duration.ofNanos(System.nanoTime() - started);
                worst = took.compareTo(worst) > 0 ? took : worst;
                // Timing a search that found nothing would prove nothing.
                assertThat(body).as(search.getKey()).contains("\"tutorId\"");
            }
            slowest.put(search.getKey(), worst);
        }

        System.out.printf("Search timings over %d seeded tutors (slowest of %d): %s%n",
                500, REPETITIONS, slowest.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue().toMillis() + "ms").toList());
        List<Duration> timings = List.copyOf(slowest.values());
        assertThat(timings).allSatisfy(took -> assertThat(took).isLessThan(SERVER_SHARE));
    }
}
