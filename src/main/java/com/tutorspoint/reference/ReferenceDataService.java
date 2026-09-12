package com.tutorspoint.reference;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceDataResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;

import java.util.List;

/**
 * Read access to the normalised reference data every filter, dropdown and tutor profile is
 * built from.
 *
 * <p>Read-only by design. Reference data is owned by Flyway migrations, not by an API:
 * changing the subject list is a schema-versioned event that ships with the code that
 * depends on it, and the Phase 6 admin package is where a curated write path will live.
 *
 * <p>Every method takes the language to render in. It is a parameter rather than something
 * read from a thread-local so that nothing below the controller has to know an HTTP request
 * exists, and so these calls are trivially testable.
 */
public interface ReferenceDataService {

    /** Active subjects in display order, each named in {@code language}. */
    List<ReferenceItemResponse> subjects(Language language);

    List<ReferenceItemResponse> examLevels(Language language);

    List<ReferenceItemResponse> syllabuses(Language language);

    /** Active districts in display order, each with its active towns nested. */
    List<DistrictResponse> districts(Language language);

    List<ReferenceItemResponse> mediums(Language language);

    List<ReferenceItemResponse> classFormats(Language language);

    /** Everything above in one response, for a client drawing a whole filter panel. */
    ReferenceDataResponse all(Language language);
}
