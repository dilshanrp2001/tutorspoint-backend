package com.tutorspoint.reference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceDataResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.reference.dto.TownResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference data end to end: the Flyway seed, the entity mapping Hibernate validates
 * against it, the translation fallback, and the three languages a caller can ask for.
 *
 * <p>This is where the seed data itself is under test. The unit tests prove the fallback
 * rule works; only a real database with the migrations applied can prove the Sinhala names
 * are actually in the table — which is the requirement easiest to satisfy badly, by
 * shipping English text in a column called {@code si}.
 */
class ReferenceDataIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void emptyTheCaches() {
        // The Spring context is shared across the whole test run, so a cache populated by an
        // earlier test would make the caching assertions below vacuous.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("reference data is readable without a token: a guest has to be able to draw the search form")
    void isPublic() throws Exception {
        mockMvc.perform(get("/api/reference/subjects")).andExpect(status().isOk());
        mockMvc.perform(get("/api/reference")).andExpect(status().isOk());
    }

    @Test
    void examLevelsComeBackInSinhalaTamilAndEnglish() throws Exception {
        assertThat(named(items("/api/reference/exam-levels", "en"), "GCE_OL")).isEqualTo("GCE O/L");
        assertThat(named(items("/api/reference/exam-levels", "si"), "GCE_OL")).isEqualTo("අ.පො.ස. සාමාන්‍ය පෙළ");
        assertThat(named(items("/api/reference/exam-levels", "ta"), "GCE_OL")).isEqualTo("க.பொ.த. சாதாரண தரம்");
    }

    @Test
    void everySeededValueIsTranslatedIntoEveryLanguage() throws Exception {
        List<String> paths = List.of("/api/reference/subjects", "/api/reference/exam-levels",
                "/api/reference/syllabuses", "/api/reference/mediums", "/api/reference/class-formats");

        for (String path : paths) {
            List<ReferenceItemResponse> english = items(path, "en");
            assertThat(english).as("%s in en", path).isNotEmpty();

            for (String tag : List.of("si", "ta")) {
                List<ReferenceItemResponse> translated = items(path, tag);

                assertThat(translated).as("%s in %s", path, tag).hasSameSizeAs(english);

                for (int i = 0; i < translated.size(); i++) {
                    ReferenceItemResponse item = translated.get(i);
                    assertThat(item.name()).as("%s / %s in %s", path, item.code(), tag).isNotBlank();

                    // A name that has decayed to the code means the fallback fired and the seed
                    // is missing a row — except where the English name is the code too, which is
                    // how an acronym like CIMA or IELTS is correctly spelled in all three.
                    if (!english.get(i).name().equals(item.code())) {
                        assertThat(item.name())
                                .as("%s / %s in %s fell back to its code", path, item.code(), tag)
                                .isNotEqualTo(item.code());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Sinhala and Tamil are real translations, not English left in the column")
    void translationsAreNotEnglishPlaceholders() throws Exception {
        List<ReferenceItemResponse> english = items("/api/reference/subjects", "en");
        List<ReferenceItemResponse> sinhala = items("/api/reference/subjects", "si");
        List<ReferenceItemResponse> tamil = items("/api/reference/subjects", "ta");

        assertThat(inScript(sinhala, 0x0D80, 0x0DFF)).as("subjects rendered in Sinhala script").isGreaterThan(60);
        assertThat(inScript(tamil, 0x0B80, 0x0BFF)).as("subjects rendered in Tamil script").isGreaterThan(60);

        // Any subject whose English name contains a lower-case letter is a translatable phrase
        // rather than an acronym like CIMA or IELTS, so it must differ in each language.
        for (int i = 0; i < english.size(); i++) {
            String code = english.get(i).code();
            String en = english.get(i).name();
            if (en.chars().anyMatch(Character::isLowerCase)) {
                assertThat(sinhala.get(i).name()).as("Sinhala name for %s", code).isNotEqualTo(en);
                assertThat(tamil.get(i).name()).as("Tamil name for %s", code).isNotEqualTo(en);
            }
        }
    }

    @Test
    void anUnsupportedOrAbsentAcceptLanguageFallsBackToEnglishRatherThanFailing() throws Exception {
        assertThat(named(items("/api/reference/exam-levels", "fr"), "GCE_AL")).isEqualTo("GCE A/L");

        List<ReferenceItemResponse> noHeader = read(
                mockMvc.perform(get("/api/reference/exam-levels")).andExpect(status().isOk()),
                new TypeReference<ApiResponse<List<ReferenceItemResponse>>>() {
                });
        assertThat(named(noHeader, "GCE_AL")).isEqualTo("GCE A/L");
    }

    @Test
    void subjectsCoverTheRealSriLankanCurriculumInDisplayOrder() throws Exception {
        List<String> codes = items("/api/reference/subjects", "en").stream()
                .map(ReferenceItemResponse::code)
                .toList();

        assertThat(codes)
                .contains("MATHEMATICS", "SCIENCE", "ENGLISH_LANGUAGE", "ICT")
                .contains("COMBINED_MATHEMATICS", "PHYSICS", "CHEMISTRY", "BIOLOGY")
                .contains("BUSINESS_STUDIES", "ACCOUNTING", "ECONOMICS")
                .contains("POLITICAL_SCIENCE", "BUDDHIST_CIVILIZATION")
                .contains("ENGINEERING_TECHNOLOGY", "BIOSYSTEMS_TECHNOLOGY", "SCIENCE_FOR_TECHNOLOGY")
                .contains("CIMA", "ACCA", "AAT")
                .doesNotHaveDuplicates();

        // O/L core before the A/L streams before the professional bodies. The list is ordered
        // by meaning, which is the only reason display_order exists.
        assertThat(codes.indexOf("MATHEMATICS")).isLessThan(codes.indexOf("COMBINED_MATHEMATICS"));
        assertThat(codes.indexOf("COMBINED_MATHEMATICS")).isLessThan(codes.indexOf("CIMA"));
    }

    @Test
    void syllabusesSeparateTheNationalMediumsFromTheInternationalBoards() throws Exception {
        assertThat(items("/api/reference/syllabuses", "en").stream().map(ReferenceItemResponse::code))
                .containsExactly("NATIONAL_SINHALA", "NATIONAL_TAMIL", "NATIONAL_ENGLISH",
                        "CAMBRIDGE", "EDEXCEL", "PROFESSIONAL_BODY", "OTHER");
    }

    @Test
    void areasNestTownsUnderTheirDistrictWithCoordinates() throws Exception {
        List<DistrictResponse> districts = read(
                mockMvc.perform(get("/api/reference/areas").header(HttpHeaders.ACCEPT_LANGUAGE, "si"))
                        .andExpect(status().isOk()),
                new TypeReference<ApiResponse<List<DistrictResponse>>>() {
                });

        assertThat(districts.stream().map(DistrictResponse::code))
                .containsExactly("COLOMBO", "GAMPAHA", "KALUTARA");

        DistrictResponse colombo = districts.getFirst();
        assertThat(colombo.name()).isEqualTo("කොළඹ");
        assertThat(colombo.towns().stream().map(TownResponse::code))
                .contains("COLOMBO_NUGEGODA", "COLOMBO_MORATUWA", "COLOMBO_MOUNT_LAVINIA");
        assertThat(townNamed(colombo.towns(), "COLOMBO_NUGEGODA")).isEqualTo("නුගේගොඩ");

        // Coordinates are what Phase 3 sorts by; a town without them is unusable then.
        assertThat(districts).flatExtracting(DistrictResponse::towns).allSatisfy(town -> {
            assertThat(town.latitude()).as("latitude of %s", town.code())
                    .isNotNull().isBetween(new BigDecimal("6.2"), new BigDecimal("7.5"));
            assertThat(town.longitude()).as("longitude of %s", town.code())
                    .isNotNull().isBetween(new BigDecimal("79.6"), new BigDecimal("80.4"));
        });
    }

    @Test
    void theBundleEndpointReturnsEveryListSoOneRequestFillsAFilterPanel() throws Exception {
        ReferenceDataResponse bundle = read(
                mockMvc.perform(get("/api/reference").header(HttpHeaders.ACCEPT_LANGUAGE, "ta"))
                        .andExpect(status().isOk()),
                new TypeReference<ApiResponse<ReferenceDataResponse>>() {
                });

        assertThat(bundle.subjects()).isNotEmpty();
        assertThat(bundle.examLevels()).hasSize(7);
        assertThat(bundle.syllabuses()).hasSize(7);
        assertThat(bundle.mediums()).hasSize(Medium.values().length);
        assertThat(bundle.classFormats()).hasSize(ClassFormat.values().length);
        assertThat(bundle.districts()).hasSize(3);

        // The same content as the per-list endpoints, in the same language: two ways to read
        // one table must not drift apart.
        assertThat(bundle.examLevels()).isEqualTo(items("/api/reference/exam-levels", "ta"));
        assertThat(bundle.subjects()).isEqualTo(items("/api/reference/subjects", "ta"));
    }

    @Test
    @DisplayName("each language is cached under its own key, so one language cannot be served another set of names")
    void isCachedPerLanguage() throws Exception {
        Cache cache = cacheManager.getCache(ReferenceDataServiceImpl.SUBJECTS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get(Language.SI)).isNull();

        items("/api/reference/subjects", "si");

        assertThat(cache.get(Language.SI)).isNotNull();
        assertThat(cache.get(Language.TA)).as("Tamil must not be served from the Sinhala entry").isNull();

        items("/api/reference/subjects", "ta");
        assertThat(cache.get(Language.TA)).isNotNull();
    }

    private List<ReferenceItemResponse> items(String path, String languageTag) throws Exception {
        return read(
                mockMvc.perform(get(path).header(HttpHeaders.ACCEPT_LANGUAGE, languageTag))
                        .andExpect(status().isOk()),
                new TypeReference<ApiResponse<List<ReferenceItemResponse>>>() {
                });
    }

    private <T> T read(ResultActions result, TypeReference<ApiResponse<T>> type) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        ApiResponse<T> envelope = objectMapper.readValue(body, type);
        assertThat(envelope.success()).isTrue();
        assertThat(envelope.error()).isNull();
        return envelope.data();
    }

    private static String named(List<ReferenceItemResponse> items, String code) {
        return items.stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reference item with code " + code))
                .name();
    }

    private static String townNamed(List<TownResponse> towns, String code) {
        return towns.stream()
                .filter(town -> town.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No town with code " + code))
                .name();
    }

    private static long inScript(List<ReferenceItemResponse> items, int from, int to) {
        return items.stream()
                .filter(item -> item.name().chars().anyMatch(c -> c >= from && c <= to))
                .count();
    }
}
