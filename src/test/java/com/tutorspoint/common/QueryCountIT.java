package com.tutorspoint.common;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.tutor.seed.DemoTutorSeeder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No N+1 queries on the two reads that carry the public site: search, and a tutor's profile.
 *
 * <p>Judged by counting the JDBC statements a request prepares, through the whole HTTP stack
 * including mapping to JSON, where lazy collections are actually touched. The test is
 * comparative on purpose: a page of fifty results must cost the same number of statements as a
 * page of five, and a tutor with many subjects and areas the same as a tutor with few. The two
 * profiles compared have the same shape - every list non-empty, a home base set, an area served
 * beyond it - because an
 * empty list or an absent home base legitimately skips a query, and that is not what an N+1 is. An
 * absolute ceiling is asserted too, so a query added per request is noticed, but the comparison
 * is what catches a query per row.
 *
 * <p>The persistence context is cleared before every measured request. MockMvc runs in the test's
 * transaction, and entities the seeding left in memory would otherwise be served without a
 * query - hiding precisely the lazy loads this is here to count.
 */
@Transactional
class QueryCountIT extends AbstractIntegrationTest {

    @Autowired
    private DemoTutorSeeder seeder;

    @Autowired
    private TutorProfileRepository profiles;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void seed() {
        seeder.seed(120, 20_260_916L);
        profiles.flush();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("a page of fifty search results costs the same statements as a page of five")
    void searchStatementsDoNotGrowWithThePage() throws Exception {
        // Page 1, not 0: the first page also counts the search for the metrics, on another thread,
        // and its statements would land in the same global statistics at an unpredictable moment.
        long small = statementsFor(get("/api/search/tutors").param("page", "1").param("size", "5"));
        long large = statementsFor(get("/api/search/tutors").param("page", "1").param("size", "50"));

        System.out.printf("Search statements: page of 5 = %d, page of 50 = %d%n", small, large);
        assertThat(large).as("statements for a page of 50 vs a page of 5").isEqualTo(small);
        assertThat(large).as("statements for one search").isLessThanOrEqualTo(25);
    }

    @Test
    @DisplayName("a rich profile costs the same statements as a sparse one")
    void profileStatementsDoNotGrowWithTheProfile() throws Exception {
        List<TutorProfile> published = profiles.findAll().stream()
                .filter(profile -> profile.getStatus() == ProfileStatus.PUBLISHED)
                .filter(QueryCountIT::hasEveryPart)
                .sorted(Comparator.comparingInt(QueryCountIT::richness))
                .toList();
        TutorProfile sparsest = published.getFirst();
        TutorProfile richest = published.getLast();
        assertThat(richness(richest)).as("the seed produced profiles of different sizes")
                .isGreaterThan(richness(sparsest));

        long sparse = statementsFor(get("/api/tutors/{id}", sparsest.getTutor().getId()));
        long rich = statementsFor(get("/api/tutors/{id}", richest.getTutor().getId()));

        System.out.printf("Profile statements: sparse (%d items) = %d, rich (%d items) = %d%n",
                richness(sparsest), sparse, richness(richest), rich);
        assertThat(rich).as("statements for the richest profile vs the sparsest").isEqualTo(sparse);
        assertThat(rich).as("statements for one profile").isLessThanOrEqualTo(13);
    }

    private long statementsFor(RequestBuilder request) throws Exception {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        mockMvc.perform(request).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private static boolean hasEveryPart(TutorProfile profile) {
        return profile.getHomeBaseArea() != null
                && !profile.getSubjects().isEmpty() && !profile.getExamLevels().isEmpty()
                && !profile.getSyllabuses().isEmpty()
                // An area besides the home base: the home base's translations are already loaded,
                // so a tutor serving only there skips the served areas' translation batch.
                && profile.getAreasServed().stream().anyMatch(area -> !area.equals(profile.getHomeBaseArea()))
                && !profile.getMediums().isEmpty() && !profile.getClassFormats().isEmpty()
                && !profile.getQualifications().isEmpty();
    }

    private static int richness(TutorProfile profile) {
        return profile.getSubjects().size() + profile.getExamLevels().size() + profile.getSyllabuses().size()
                + profile.getAreasServed().size() + profile.getMediums().size() + profile.getClassFormats().size()
                + profile.getQualifications().size();
    }
}
