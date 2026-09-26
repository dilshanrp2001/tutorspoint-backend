package com.tutorspoint.reference;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceDataResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reference data, read once per language and then served from memory.
 *
 * <p>Caching is safe to the point of being boring here: the rows are written only by Flyway,
 * which runs before the first request, so a cached list cannot drift from the table while
 * the process is alive. The key is the language — the same subject list rendered three ways
 * is three different responses and must not share an entry.
 *
 * <p>{@link #all} is cached in its own right rather than composed from the cached methods
 * above it. A call from inside this class does not pass through the caching proxy, so
 * without its own annotation it would re-query on every request; with it, the assembled
 * response is built at most once per language.
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataServiceImpl implements ReferenceDataService {

    static final String SUBJECTS_CACHE = "referenceSubjects";
    static final String EXAM_LEVELS_CACHE = "referenceExamLevels";
    static final String SYLLABUSES_CACHE = "referenceSyllabuses";
    static final String DISTRICTS_CACHE = "referenceDistricts";
    static final String ENUMS_CACHE = "referenceEnums";
    static final String BUNDLE_CACHE = "referenceBundle";

    private final SubjectRepository subjects;
    private final ExamLevelRepository examLevels;
    private final SyllabusRepository syllabuses;
    private final AreaRepository areas;
    private final ReferenceMapper mapper;
    private final ReferenceLabels labels;

    @Override
    @Cacheable(cacheNames = SUBJECTS_CACHE, key = "#language")
    @Transactional(readOnly = true)
    public List<ReferenceItemResponse> subjects(Language language) {
        return mapper.toSubjects(subjects.findActiveWithTranslations(), language);
    }

    @Override
    @Cacheable(cacheNames = EXAM_LEVELS_CACHE, key = "#language")
    @Transactional(readOnly = true)
    public List<ReferenceItemResponse> examLevels(Language language) {
        return mapper.toExamLevels(examLevels.findActiveWithTranslations(), language);
    }

    @Override
    @Cacheable(cacheNames = SYLLABUSES_CACHE, key = "#language")
    @Transactional(readOnly = true)
    public List<ReferenceItemResponse> syllabuses(Language language) {
        return mapper.toSyllabuses(syllabuses.findActiveWithTranslations(), language);
    }

    @Override
    @Cacheable(cacheNames = DISTRICTS_CACHE, key = "#language")
    @Transactional(readOnly = true)
    public List<DistrictResponse> districts(Language language) {
        return mapper.toDistricts(areas.findActiveDistrictsWithTowns(), language);
    }

    @Override
    @Cacheable(cacheNames = ENUMS_CACHE, key = "'mediums:' + #language")
    public List<ReferenceItemResponse> mediums(Language language) {
        return labels.mediums(language);
    }

    @Override
    @Cacheable(cacheNames = ENUMS_CACHE, key = "'classFormats:' + #language")
    public List<ReferenceItemResponse> classFormats(Language language) {
        return labels.classFormats(language);
    }

    @Override
    @Cacheable(cacheNames = BUNDLE_CACHE, key = "#language")
    @Transactional(readOnly = true)
    public ReferenceDataResponse all(Language language) {
        return new ReferenceDataResponse(
                mapper.toSubjects(subjects.findActiveWithTranslations(), language),
                mapper.toExamLevels(examLevels.findActiveWithTranslations(), language),
                mapper.toSyllabuses(syllabuses.findActiveWithTranslations(), language),
                labels.mediums(language),
                labels.classFormats(language),
                mapper.toDistricts(areas.findActiveDistrictsWithTowns(), language));
    }
}
