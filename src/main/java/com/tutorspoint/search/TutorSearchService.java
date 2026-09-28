package com.tutorspoint.search;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.search.dto.TutorSearchCriteria;
import com.tutorspoint.search.dto.TutorSearchResponse;

/** Tutor search (FR-S1 - FR-S7). Open to guests. */
public interface TutorSearchService {

    /**
     * One page of published tutors matching every filter in the criteria, ranked by the
     * strategy it names with featured tutors pinned above, plus facet counts.
     *
     * @param language the language reference values on the cards are rendered in
     */
    TutorSearchResponse search(TutorSearchCriteria criteria, Language language);
}
