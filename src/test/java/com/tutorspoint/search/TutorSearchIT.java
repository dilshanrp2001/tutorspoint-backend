package com.tutorspoint.search;

import com.jayway.jsonpath.JsonPath;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import com.tutorspoint.search.featured.FeaturedTutorLookup;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search against the real schema, the real triggers and the real filter chain.
 *
 * <p>Five visible tutors, chosen so that every filter separates them differently, and four
 * that must never appear - a draft, an unpublished profile, a suspended profile, and a published
 * profile whose account is suspended. All four teach Chemistry, which is the point: any leak shows
 * up the moment anything searches for Chemistry.
 *
 * <p>The keyword and distance tests exercise the functions and triggers in
 * {@code V7__tutor_search.sql}, which nothing short of PostgreSQL can.
 *
 * <p>{@link FeaturedTutorLookup} is a mock so the sponsored slot can be filled; unstubbed it
 * returns an empty set, which is exactly what the placeholder does.
 */
@Transactional
class TutorSearchIT extends AbstractIntegrationTest {

    private static final String SEARCH = "/api/search/tutors";

    @Autowired
    private UserRepository users;
    @Autowired
    private TutorProfileRepository profiles;
    @Autowired
    private SubjectRepository subjects;
    @Autowired
    private ExamLevelRepository examLevels;
    @Autowired
    private SyllabusRepository syllabuses;
    @Autowired
    private AreaRepository areas;

    @MockitoBean
    private FeaturedTutorLookup featuredTutors;

    /** Tutor id to fixture name, for every tutor created - visible or not. */
    private final Map<Long, String> names = new HashMap<>();
    private final Map<String, TutorProfile> byName = new HashMap<>();
    private int sequence;

    @BeforeEach
    void createTutors() {
        // Verified, accepting, well reviewed. The only visible Chemistry tutor.
        tutor("chem").subjects("CHEMISTRY", "PHYSICS").examLevels("GCE_AL").syllabuses("NATIONAL_ENGLISH")
                .mediums(Medium.ENGLISH).formats(ClassFormat.SMALL_GROUP)
                .serves("COLOMBO_NUGEGODA").homeBase("COLOMBO_NUGEGODA")
                .fee("2000", "3000", FeeUnit.PER_MONTH).years(15).verified().rated("4.80", 40)
                .text("A/L Chemistry specialist", "Fifteen years of A/L classes. I also help with Biology practicals.")
                .publish();
        tutor("maths").subjects("COMBINED_MATHEMATICS").examLevels("GCE_AL").syllabuses("NATIONAL_SINHALA")
                .mediums(Medium.SINHALA).formats(ClassFormat.MASS_CLASS)
                .serves("COLOMBO_MAHARAGAMA").homeBase("COLOMBO_MAHARAGAMA")
                .fee("1200", "1500", FeeUnit.PER_MONTH).years(8).rated("4.20", 10)
                .text("Combined Maths mass classes", "Theory and revision for A/L.")
                .publish();
        tutor("english").subjects("ENGLISH_LANGUAGE").examLevels("GCE_OL").syllabuses("CAMBRIDGE")
                .mediums(Medium.ENGLISH).formats(ClassFormat.ONE_TO_ONE, ClassFormat.ONLINE).online()
                .serves("GAMPAHA_NEGOMBO").homeBase("GAMPAHA_NEGOMBO")
                .fee("3000", "4000", FeeUnit.PER_HOUR).years(20).verified().availability(AvailabilityStatus.LIMITED)
                .text("English for Cambridge O/L", "One-to-one lessons in Negombo or online.")
                .publish();
        // Serves an area but has no home base, so distance cannot be measured to it.
        tutor("ict").subjects("ICT").examLevels("GCE_OL").syllabuses("NATIONAL_TAMIL")
                .mediums(Medium.TAMIL).formats(ClassFormat.ONLINE).online()
                .serves("COLOMBO_BORELLA")
                .fee("1000", "2000", FeeUnit.PER_CLASS).years(3).rated("3.50", 2).availability(AvailabilityStatus.FULL)
                .text("ICT online classes", "Practical ICT for O/L students.")
                .publish();
        // Lists the whole district rather than a town.
        tutor("district").subjects("BIOLOGY").examLevels("GCE_AL").syllabuses("NATIONAL_SINHALA")
                .mediums(Medium.SINHALA).formats(ClassFormat.SMALL_GROUP)
                .serves("COLOMBO").homeBase("COLOMBO_KOTTAWA")
                .fee("2500", "3500", FeeUnit.PER_MONTH).years(11).rated("4.80", 12)
                .text("A/L Biology small groups", "Biology for A/L students across Colombo.")
                .publish();

        tutor("draft").chemistryTutor().draft();
        tutor("unpublished").chemistryTutor().unpublished();
        tutor("suspended").chemistryTutor().suspendedProfile();
        tutor("suspendedAccount").chemistryTutor().suspendedAccount();

        profiles.flush();
    }

    @Nested
    @DisplayName("visibility")
    class Visibility {

        @Test
        @DisplayName("an unfiltered search returns every published profile of an active account, and nothing else")
        void onlyPublishedActiveProfilesAppear() throws Exception {
            mockMvc.perform(get(SEARCH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalResults").value(5))
                    .andExpect(jsonPath("$.data.sort").value("relevance"));

            assertThat(resultNames(get(SEARCH))).containsExactlyInAnyOrder("chem", "maths", "english", "ict", "district");
        }

        @Test
        @DisplayName("draft, unpublished and suspended profiles stay hidden even from a search they would match")
        void hiddenProfilesNeverMatch() throws Exception {
            assertThat(resultNames(get(SEARCH).param("subject", "CHEMISTRY"))).containsExactly("chem");
            assertThat(resultNames(get(SEARCH).param("keyword", "chemistry"))).containsExactly("chem");
            assertThat(resultNames(get(SEARCH).param("subject", "CHEMISTRY").param("area", "COLOMBO")
                    .param("minFee", "0").param("maxFee", "100000"))).containsExactly("chem");

            // Nor are they counted: the facet beside Chemistry is one, not five.
            mockMvc.perform(get(SEARCH))
                    .andExpect(jsonPath("$.data.facets.subjects[?(@.code=='CHEMISTRY')].count").value(1));
        }

        @Test
        @DisplayName("search needs no token, and a card carries no contact details")
        void publicAndContactFree() throws Exception {
            String body = mockMvc.perform(get(SEARCH).param("subject", "CHEMISTRY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.results[0].tutor.fullName").value("Tutor chem"))
                    .andExpect(jsonPath("$.data.results[0].tutor.email").doesNotExist())
                    .andExpect(jsonPath("$.data.results[0].tutor.phoneNumber").doesNotExist())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(body).doesNotContain("@search.example.lk", "+9477");
        }
    }

    @Nested
    @DisplayName("each filter, alone")
    class SingleFilters {

        static Stream<Arguments> filters() {
            return Stream.of(
                    Arguments.of("subject", "CHEMISTRY", List.of("chem")),
                    Arguments.of("subject", "chemistry", List.of("chem")),
                    Arguments.of("examLevel", "GCE_OL", List.of("english", "ict")),
                    Arguments.of("syllabus", "NATIONAL_SINHALA", List.of("maths", "district")),
                    Arguments.of("medium", "TAMIL", List.of("ict")),
                    Arguments.of("classFormat", "ONLINE", List.of("english", "ict")),
                    // A town matches tutors listing it, and tutors listing its whole district.
                    Arguments.of("area", "COLOMBO_NUGEGODA", List.of("chem", "district")),
                    Arguments.of("area", "COLOMBO_MAHARAGAMA", List.of("maths", "district")),
                    // A district matches tutors listing it or any of its towns.
                    Arguments.of("area", "COLOMBO", List.of("chem", "maths", "ict", "district")),
                    Arguments.of("area", "GAMPAHA", List.of("english")),
                    // The price band matches any overlap with a tutor's range, ends included.
                    Arguments.of("minFee", "3500", List.of("english", "district")),
                    Arguments.of("maxFee", "1200", List.of("maths", "ict")),
                    Arguments.of("availableOnline", "true", List.of("english", "ict")),
                    Arguments.of("availableOnline", "false", List.of("chem", "maths", "english", "ict", "district")),
                    Arguments.of("verifiedOnly", "true", List.of("chem", "english")),
                    // An unreviewed tutor clears no minimum.
                    Arguments.of("minRating", "4.5", List.of("chem", "district")),
                    Arguments.of("minRating", "0", List.of("chem", "maths", "ict", "district")),
                    Arguments.of("keyword", "chem", List.of("chem")),
                    Arguments.of("keyword", "A/L biology", List.of("chem", "district")),
                    // Subject names are searchable in all three languages, by prefix.
                    Arguments.of("keyword", "ගණිත", List.of("maths")),
                    Arguments.of("keyword", "தகவல்", List.of("ict")),
                    // Punctuation alone is no words at all, and filters nothing.
                    Arguments.of("keyword", "!!!", List.of("chem", "maths", "english", "ict", "district")),
                    // A code that names nothing matches nobody; it is not an error.
                    Arguments.of("subject", "ASTROLOGY", List.of()),
                    Arguments.of("area", "ATLANTIS", List.of()));
        }

        @ParameterizedTest(name = "{0}={1}")
        @MethodSource("filters")
        void narrowsByExactlyThatCondition(String parameter, String value, List<String> expected) throws Exception {
            assertThat(resultNames(get(SEARCH).param(parameter, value))).containsExactlyInAnyOrderElementsOf(expected);
            mockMvc.perform(get(SEARCH).param(parameter, value))
                    .andExpect(jsonPath("$.data.totalResults").value(expected.size()));
        }
    }

    @Nested
    @DisplayName("filters combined")
    class CombinedFilters {

        @Test
        @DisplayName("every filter given must hold")
        void filtersIntersect() throws Exception {
            assertThat(resultNames(get(SEARCH).param("examLevel", "GCE_AL").param("area", "COLOMBO")))
                    .containsExactlyInAnyOrder("chem", "maths", "district");
            assertThat(resultNames(get(SEARCH).param("examLevel", "GCE_AL").param("area", "COLOMBO")
                    .param("medium", "SINHALA"))).containsExactlyInAnyOrder("maths", "district");
            assertThat(resultNames(get(SEARCH).param("examLevel", "GCE_AL").param("area", "COLOMBO")
                    .param("medium", "SINHALA").param("minRating", "4.5"))).containsExactly("district");
            assertThat(resultNames(get(SEARCH).param("minFee", "1000").param("maxFee", "2000")
                    .param("availableOnline", "true"))).containsExactly("ict");
        }

        @Test
        @DisplayName("a keyword narrows the structured filters rather than replacing them")
        void keywordCombinesWithFilters() throws Exception {
            assertThat(resultNames(get(SEARCH).param("keyword", "biology"))).containsExactlyInAnyOrder("chem", "district");
            assertThat(resultNames(get(SEARCH).param("keyword", "biology").param("verifiedOnly", "true")))
                    .containsExactly("chem");
            assertThat(resultNames(get(SEARCH).param("keyword", "chem").param("area", "GAMPAHA"))).isEmpty();
        }

        @Test
        @DisplayName("the keyword document follows the subjects a tutor teaches")
        void keywordDocumentTracksSubjectChanges() throws Exception {
            assertThat(resultNames(get(SEARCH).param("keyword", "physics"))).containsExactly("chem");

            TutorProfile chem = byName.get("chem");
            // Copies: the getters are views of the collections teaches() is about to replace.
            chem.teaches(subjects.findByCodeInAndActiveTrue(List.of("CHEMISTRY")),
                    List.copyOf(chem.getExamLevels()), List.copyOf(chem.getSyllabuses()), List.copyOf(chem.getMediums()));
            profiles.flush();

            assertThat(resultNames(get(SEARCH).param("keyword", "physics"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("ranking")
    class Ranking {

        @Test
        @DisplayName("price_asc: cheapest starting fee first")
        void byPrice() throws Exception {
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc")))
                    .containsExactly("ict", "maths", "chem", "district", "english");
        }

        @Test
        @DisplayName("rating_desc: best average first, more reviews breaking a tie, the unreviewed last")
        void byRating() throws Exception {
            assertThat(resultNames(get(SEARCH).param("sort", "rating_desc")))
                    .containsExactly("chem", "district", "maths", "ict", "english");
        }

        @Test
        @DisplayName("experience_desc: most years first")
        void byExperience() throws Exception {
            assertThat(resultNames(get(SEARCH).param("sort", "experience_desc")))
                    .containsExactly("english", "chem", "district", "maths", "ict");
        }

        @Test
        @DisplayName("distance_asc: nearest home base to the chosen area first, no home base last")
        void byDistance() throws Exception {
            // From the centre of Colombo: Nugegoda ~8 km, Maharagama ~11 km, Kottawa ~14 km.
            assertThat(resultNames(get(SEARCH).param("sort", "distance_asc").param("area", "COLOMBO")))
                    .containsExactly("chem", "maths", "district", "ict");
        }

        @Test
        @DisplayName("relevance without a keyword: verified, then with room, then best reviewed")
        void byRelevance() throws Exception {
            assertThat(resultNames(get(SEARCH))).containsExactly("chem", "english", "district", "maths", "ict");
        }

        @Test
        @DisplayName("relevance with a keyword: the closer match leads, even over a verified tutor")
        void byKeywordRelevance() throws Exception {
            // district has Biology as its subject and in its headline; chem mentions it once, in its bio.
            assertThat(resultNames(get(SEARCH).param("keyword", "biology"))).containsExactly("district", "chem");
        }

        @Test
        @DisplayName("pages partition the ranking: no tutor repeated, none skipped")
        void pagesPartitionTheOrdering() throws Exception {
            MockHttpServletRequestBuilder first = get(SEARCH).param("sort", "price_asc").param("size", "2");
            mockMvc.perform(first)
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.totalResults").value(5));

            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "2").param("page", "0")))
                    .containsExactly("ict", "maths");
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "2").param("page", "1")))
                    .containsExactly("chem", "district");
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "2").param("page", "2")))
                    .containsExactly("english");
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "2").param("page", "3")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("featured placement")
    class Featured {

        @Test
        @DisplayName("featured tutors sit above the organic results in the chosen order, flagged, and appear once")
        void featuredArePinnedAndFlagged() throws Exception {
            when(featuredTutors.featuredProfileIds()).thenReturn(ids("english", "maths"));

            mockMvc.perform(get(SEARCH).param("sort", "price_asc"))
                    .andExpect(jsonPath("$.data.totalResults").value(5))
                    .andExpect(jsonPath("$.data.results[0].featured").value(true))
                    .andExpect(jsonPath("$.data.results[1].featured").value(true))
                    .andExpect(jsonPath("$.data.results[2].featured").value(false))
                    .andExpect(jsonPath("$.data.results[4].featured").value(false));

            // Featured by price among themselves, then the rest by price.
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc")))
                    .containsExactly("maths", "english", "ict", "chem", "district");
            assertThat(resultNames(get(SEARCH).param("sort", "experience_desc")))
                    .containsExactly("english", "maths", "chem", "district", "ict");
        }

        @Test
        @DisplayName("a featured tutor who does not match the filters is not shown")
        void featuredMustStillMatch() throws Exception {
            when(featuredTutors.featuredProfileIds()).thenReturn(ids("english", "maths"));

            assertThat(resultNames(get(SEARCH).param("subject", "CHEMISTRY"))).containsExactly("chem");
            mockMvc.perform(get(SEARCH).param("subject", "CHEMISTRY"))
                    .andExpect(jsonPath("$.data.results[0].featured").value(false));
        }

        @Test
        @DisplayName("at most three featured to a page; the rest carry over to the next page's slots")
        void atMostThreePerPage() throws Exception {
            when(featuredTutors.featuredProfileIds()).thenReturn(ids("chem", "maths", "ict", "district"));

            mockMvc.perform(get(SEARCH).param("sort", "price_asc").param("size", "4"))
                    .andExpect(jsonPath("$.data.results[?(@.featured == true)]", hasSize(3)))
                    .andExpect(jsonPath("$.data.results[3].featured").value(false));
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "4")))
                    .containsExactly("ict", "maths", "chem", "english");
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "4").param("page", "1")))
                    .containsExactly("district");
        }

        @Test
        @DisplayName("with no organic results left to protect, featured tutors fill the page")
        void featuredFillPagesOnceOrganicRunsOut() throws Exception {
            when(featuredTutors.featuredProfileIds()).thenReturn(ids("chem", "maths", "english", "ict", "district"));

            mockMvc.perform(get(SEARCH).param("sort", "price_asc").param("size", "4"))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.results[?(@.featured == true)]", hasSize(4)));
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "4")))
                    .containsExactly("ict", "maths", "chem", "district");
            assertThat(resultNames(get(SEARCH).param("sort", "price_asc").param("size", "4").param("page", "1")))
                    .containsExactly("english");
        }
    }

    @Nested
    @DisplayName("facets")
    class Facets {

        @Test
        @DisplayName("count what each value would return, leaving that facet's own filter out")
        void countsPerValue() throws Exception {
            mockMvc.perform(get(SEARCH).param("subject", "CHEMISTRY"))
                    .andExpect(jsonPath("$.data.totalResults").value(1))
                    // Subjects ignore the subject filter: choosing Biology instead would give one.
                    .andExpect(jsonPath("$.data.facets.subjects[?(@.code=='BIOLOGY')].count").value(1))
                    .andExpect(jsonPath("$.data.facets.subjects[?(@.code=='CHEMISTRY')].count").value(1))
                    // Every other facet applies it: only chem is left to count.
                    .andExpect(jsonPath("$.data.facets.mediums.length()").value(1))
                    .andExpect(jsonPath("$.data.facets.mediums[0].code").value("ENGLISH"))
                    .andExpect(jsonPath("$.data.facets.examLevels[0].code").value("GCE_AL"))
                    .andExpect(jsonPath("$.data.facets.examLevels[0].count").value(1));
        }

        @Test
        @DisplayName("area counts match what the area filter returns, for districts and towns")
        void areaCountsFollowCoverage() throws Exception {
            mockMvc.perform(get(SEARCH))
                    .andExpect(jsonPath("$.data.facets.areas[?(@.code=='COLOMBO')].count").value(4))
                    .andExpect(jsonPath("$.data.facets.areas[?(@.code=='COLOMBO_NUGEGODA')].count").value(2))
                    .andExpect(jsonPath("$.data.facets.areas[?(@.code=='COLOMBO_KOTTAWA')].count").value(1))
                    .andExpect(jsonPath("$.data.facets.areas[?(@.code=='GAMPAHA')].count").value(1))
                    .andExpect(jsonPath("$.data.facets.areas[?(@.code=='KALUTARA')]").isEmpty())
                    .andExpect(jsonPath("$.data.facets.classFormats[?(@.code=='ONLINE')].count").value(2));
        }

        @Test
        @DisplayName("are still counted when nothing matches, so the parent can see a way out")
        void countedForAnEmptyResult() throws Exception {
            mockMvc.perform(get(SEARCH).param("subject", "CHEMISTRY").param("medium", "TAMIL"))
                    .andExpect(jsonPath("$.data.totalResults").value(0))
                    .andExpect(jsonPath("$.data.results").isEmpty())
                    .andExpect(jsonPath("$.data.facets.mediums[?(@.code=='ENGLISH')].count").value(1))
                    .andExpect(jsonPath("$.data.facets.subjects[?(@.code=='ICT')].count").value(1));
        }
    }

    @Nested
    @DisplayName("the request")
    class Request {

        @Test
        @DisplayName("an oversized page, an unknown sort and an inverted price band are rejected at the edge")
        void invalidParametersAreRejected() throws Exception {
            mockMvc.perform(get(SEARCH).param("size", "51").param("sort", "cheapest")
                            .param("minFee", "5000").param("maxFee", "1000").param("minRating", "6"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.fieldErrors.size").exists())
                    .andExpect(jsonPath("$.error.fieldErrors.sort").exists())
                    .andExpect(jsonPath("$.error.fieldErrors.feeBandOrdered").exists())
                    .andExpect(jsonPath("$.error.fieldErrors.minRating").exists());
        }

        @Test
        @DisplayName("distance sorting needs an area to measure from")
        void distanceNeedsAnArea() throws Exception {
            mockMvc.perform(get(SEARCH).param("sort", "distance_asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors.distanceSortAnchored").exists());
        }

        @Test
        @DisplayName("a value that cannot be converted is rejected without naming Java types")
        void unconvertibleValues() throws Exception {
            String body = mockMvc.perform(get(SEARCH).param("medium", "KLINGON").param("page", "two"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors.medium").value("is invalid"))
                    .andExpect(jsonPath("$.error.fieldErrors.page").value("is invalid"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(body).doesNotContain("java.", "com.tutorspoint");
        }

        @Test
        @DisplayName("validation messages come back in the caller's language")
        void messagesAreLocalised() throws Exception {
            mockMvc.perform(get(SEARCH).param("size", "0").header("Accept-Language", "ta"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors.size")
                            .value("ஒரு பக்கத்திற்கான முடிவுகள் 1 முதல் 50 வரை இருக்க வேண்டும்"));
        }

        @Test
        @DisplayName("sort keys are case-insensitive and blank parameters are ignored")
        void lenientNormalisation() throws Exception {
            mockMvc.perform(get(SEARCH).param("sort", "PRICE_ASC").param("subject", " ").param("keyword", "  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sort").value("price_asc"))
                    .andExpect(jsonPath("$.data.totalResults").value(5));
        }

        @Test
        @DisplayName("the OpenAPI contract lists exactly the search parameters, and not the cross-field checks")
        void documentedParameters() throws Exception {
            String docs = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            List<String> parameters = JsonPath.read(docs, "$.paths['/api/search/tutors'].get.parameters[*].name");

            assertThat(parameters).containsExactlyInAnyOrder("subject", "examLevel", "syllabus", "medium",
                    "classFormat", "area", "minFee", "maxFee", "availableOnline", "verifiedOnly", "minRating",
                    "keyword", "sort", "page", "size");
        }

        @Test
        @DisplayName("card reference values render in the caller's language")
        void cardsAreLocalised() throws Exception {
            mockMvc.perform(get(SEARCH).param("subject", "ICT").header("Accept-Language", "ta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.results[0].tutor.mediums[0].code").value("TAMIL"))
                    .andExpect(jsonPath("$.data.results[0].tutor.mediums[0].name").value("தமிழ்"))
                    .andExpect(jsonPath("$.data.results[0].tutor.subjects[0].name")
                            .value("தகவல் மற்றும் தொடர்பாடல் தொழினுட்பம்"))
                    .andExpect(jsonPath("$.data.results[0].tutor.averageRating").value(3.5))
                    .andExpect(jsonPath("$.data.results[0].tutor.reviewCount").value(2));
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<String> resultNames(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> tutorIds = JsonPath.read(body, "$.data.results[*].tutor.tutorId");
        return tutorIds.stream().map(id -> names.getOrDefault(id.longValue(), "unknown#" + id)).toList();
    }

    private Set<Long> ids(String... fixtureNames) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : fixtureNames) {
            ids.add(byName.get(name).getId());
        }
        return ids;
    }

    private TutorBuilder tutor(String name) {
        return new TutorBuilder(name);
    }

    /** A tutor and a complete profile, built through the same entity methods the wizard uses. */
    private final class TutorBuilder {

        private final String name;
        private List<String> subjectCodes = List.of();
        private List<String> examLevelCodes = List.of();
        private List<String> syllabusCodes = List.of();
        private Set<Medium> mediums = Set.of();
        private Set<ClassFormat> formats = Set.of();
        private List<String> servedCodes = List.of();
        private String homeBaseCode;
        private boolean online;
        private String feeMin;
        private String feeMax;
        private FeeUnit feeUnit;
        private int years;
        private boolean verified;
        private String rating;
        private int reviews;
        private AvailabilityStatus availability = AvailabilityStatus.ACCEPTING;
        private String headline;
        private String bio;

        TutorBuilder(String name) {
            this.name = name;
        }

        TutorBuilder subjects(String... codes) {
            subjectCodes = List.of(codes);
            return this;
        }

        TutorBuilder examLevels(String... codes) {
            examLevelCodes = List.of(codes);
            return this;
        }

        TutorBuilder syllabuses(String... codes) {
            syllabusCodes = List.of(codes);
            return this;
        }

        TutorBuilder mediums(Medium... values) {
            mediums = Set.of(values);
            return this;
        }

        TutorBuilder formats(ClassFormat... values) {
            formats = Set.of(values);
            return this;
        }

        TutorBuilder serves(String... codes) {
            servedCodes = List.of(codes);
            return this;
        }

        TutorBuilder homeBase(String code) {
            homeBaseCode = code;
            return this;
        }

        TutorBuilder online() {
            online = true;
            return this;
        }

        TutorBuilder fee(String min, String max, FeeUnit unit) {
            feeMin = min;
            feeMax = max;
            feeUnit = unit;
            return this;
        }

        TutorBuilder years(int value) {
            years = value;
            return this;
        }

        TutorBuilder verified() {
            verified = true;
            return this;
        }

        TutorBuilder rated(String average, int count) {
            rating = average;
            reviews = count;
            return this;
        }

        TutorBuilder availability(AvailabilityStatus value) {
            availability = value;
            return this;
        }

        TutorBuilder text(String headlineText, String bioText) {
            headline = headlineText;
            bio = bioText;
            return this;
        }

        /** A profile that matches any Chemistry search, for the ones that must stay hidden. */
        TutorBuilder chemistryTutor() {
            return subjects("CHEMISTRY").examLevels("GCE_AL").syllabuses("NATIONAL_ENGLISH")
                    .mediums(Medium.ENGLISH).formats(ClassFormat.SMALL_GROUP)
                    .serves("COLOMBO_NUGEGODA").homeBase("COLOMBO_NUGEGODA")
                    .fee("1500", "2500", FeeUnit.PER_MONTH).years(30).verified().rated("5.00", 99)
                    .text("A/L Chemistry " + name, "Chemistry for A/L.");
        }

        TutorProfile publish() {
            TutorProfile profile = build();
            profile.publish();
            return profile;
        }

        void draft() {
            build();
        }

        void unpublished() {
            publish().unpublish();
        }

        void suspendedProfile() {
            publish().suspend();
        }

        void suspendedAccount() {
            TutorProfile profile = publish();
            profile.getTutor().suspend();
        }

        private TutorProfile build() {
            sequence++;
            Tutor tutor = new Tutor("tutor." + name.toLowerCase() + "@search.example.lk", "not-a-real-hash",
                    "Tutor " + name, "+9477%07d".formatted(4_000_000 + sequence), Language.EN);
            tutor.verifyEmail();
            tutor.verifyPhone();
            tutor.activate();
            users.save(tutor);

            TutorProfile profile = profiles.save(new TutorProfile(tutor));
            profile.describe(headline, bio);
            profile.teaches(subjects.findByCodeInAndActiveTrue(subjectCodes),
                    examLevels.findByCodeInAndActiveTrue(examLevelCodes),
                    syllabuses.findByCodeInAndActiveTrue(syllabusCodes),
                    mediums);
            profile.delivers(formats, online, availability);
            profile.servesAreas(homeBaseCode == null ? null : areas.findByCode(homeBaseCode).orElseThrow(),
                    areas.findByCodeInAndActiveTrue(servedCodes), 10);
            profile.recordExperience(years);
            profile.chargesBetween(new BigDecimal(feeMin), new BigDecimal(feeMax), feeUnit);
            profile.attachPhoto("/api/media/photos/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.jpg");
            if (verified) {
                profile.verify(Instant.parse("2026-08-01T00:00:00Z"));
            }
            if (rating != null) {
                profile.summariseReviews(new BigDecimal(rating), reviews);
            }

            names.put(tutor.getId(), name);
            byName.put(name, profile);
            return profile;
        }
    }
}
