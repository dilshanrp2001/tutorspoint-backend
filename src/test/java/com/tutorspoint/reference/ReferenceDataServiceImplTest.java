package com.tutorspoint.reference;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceDataResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * What this service is responsible for is narrow but easy to get wrong: read the active
 * rows, and hand the caller's language on to whatever renders them.
 *
 * <p>So that is what is asserted — that each list comes from its own repository's
 * translation-fetching finder, and that the language reaches the mapper unchanged. Caching
 * is a proxy concern and absent here by design; {@link ReferenceDataIT} covers it against
 * the real container.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceImplTest {

    private static final List<ReferenceItemResponse> MAPPED = List.of(new ReferenceItemResponse("X", "x"));

    @Mock
    private SubjectRepository subjects;

    @Mock
    private ExamLevelRepository examLevels;

    @Mock
    private SyllabusRepository syllabuses;

    @Mock
    private AreaRepository areas;

    @Mock
    private ReferenceMapper mapper;

    @Mock
    private ReferenceLabels labels;

    @InjectMocks
    private ReferenceDataServiceImpl service;

    @Test
    void subjectsAreReadWithTheirTranslationsAndRenderedInTheRequestedLanguage() {
        List<Subject> rows = List.of(new Subject("MATHEMATICS", 115));
        given(subjects.findActiveWithTranslations()).willReturn(rows);
        given(mapper.toSubjects(rows, Language.TA)).willReturn(MAPPED);

        assertThat(service.subjects(Language.TA)).isEqualTo(MAPPED);
        then(mapper).should().toSubjects(rows, Language.TA);
    }

    @Test
    void examLevelsAreReadWithTheirTranslations() {
        List<ExamLevel> rows = List.of(new ExamLevel("GCE_OL", 30));
        given(examLevels.findActiveWithTranslations()).willReturn(rows);
        given(mapper.toExamLevels(rows, Language.SI)).willReturn(MAPPED);

        assertThat(service.examLevels(Language.SI)).isEqualTo(MAPPED);
    }

    @Test
    void syllabusesAreReadWithTheirTranslations() {
        List<Syllabus> rows = List.of(new Syllabus("CAMBRIDGE", 40));
        given(syllabuses.findActiveWithTranslations()).willReturn(rows);
        given(mapper.toSyllabuses(rows, Language.EN)).willReturn(MAPPED);

        assertThat(service.syllabuses(Language.EN)).isEqualTo(MAPPED);
    }

    @Test
    void areasAreReadAsDistrictsCarryingTheirTowns() {
        List<Area> rows = List.of(colombo());
        List<DistrictResponse> mapped = List.of(new DistrictResponse("COLOMBO", "කොළඹ", null, null, List.of()));
        given(areas.findActiveDistrictsWithTowns()).willReturn(rows);
        given(mapper.toDistricts(rows, Language.SI)).willReturn(mapped);

        assertThat(service.districts(Language.SI)).isEqualTo(mapped);
        // Never findAll(): an inactive area must not reach a dropdown.
        then(areas).should().findActiveDistrictsWithTowns();
        then(areas).shouldHaveNoMoreInteractions();
    }

    @Test
    void theEnumListsComeFromTheMessageBundleRatherThanATable() {
        given(labels.mediums(Language.TA)).willReturn(MAPPED);
        given(labels.classFormats(Language.TA)).willReturn(MAPPED);

        assertThat(service.mediums(Language.TA)).isEqualTo(MAPPED);
        assertThat(service.classFormats(Language.TA)).isEqualTo(MAPPED);
    }

    @Test
    void theBundleCarriesEveryListInOneResponse() {
        List<Subject> subjectRows = List.of(new Subject("MATHEMATICS", 115));
        List<ExamLevel> examLevelRows = List.of(new ExamLevel("GCE_OL", 30));
        List<Syllabus> syllabusRows = List.of(new Syllabus("CAMBRIDGE", 40));
        List<Area> areaRows = List.of(colombo());
        List<DistrictResponse> districts = List.of(new DistrictResponse("COLOMBO", "Colombo", null, null, List.of()));

        given(subjects.findActiveWithTranslations()).willReturn(subjectRows);
        given(examLevels.findActiveWithTranslations()).willReturn(examLevelRows);
        given(syllabuses.findActiveWithTranslations()).willReturn(syllabusRows);
        given(areas.findActiveDistrictsWithTowns()).willReturn(areaRows);
        given(mapper.toSubjects(subjectRows, Language.EN)).willReturn(MAPPED);
        given(mapper.toExamLevels(examLevelRows, Language.EN)).willReturn(MAPPED);
        given(mapper.toSyllabuses(syllabusRows, Language.EN)).willReturn(MAPPED);
        given(mapper.toDistricts(areaRows, Language.EN)).willReturn(districts);
        given(labels.mediums(Language.EN)).willReturn(MAPPED);
        given(labels.classFormats(Language.EN)).willReturn(MAPPED);

        ReferenceDataResponse bundle = service.all(Language.EN);

        assertThat(bundle.subjects()).isEqualTo(MAPPED);
        assertThat(bundle.examLevels()).isEqualTo(MAPPED);
        assertThat(bundle.syllabuses()).isEqualTo(MAPPED);
        assertThat(bundle.mediums()).isEqualTo(MAPPED);
        assertThat(bundle.classFormats()).isEqualTo(MAPPED);
        assertThat(bundle.districts()).isEqualTo(districts);
    }

    private static Area colombo() {
        return Area.district("COLOMBO", 10, new BigDecimal("6.927100"), new BigDecimal("79.861200"));
    }
}
