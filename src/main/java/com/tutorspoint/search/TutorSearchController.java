package com.tutorspoint.search;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.search.dto.TutorSearchCriteria;
import com.tutorspoint.search.dto.TutorSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Tutor search (FR-S1 - FR-S7). Public: no token is needed to look.
 *
 * <p>A GET with the filters in the query string, so a search is a URL - shareable, bookmarkable,
 * and cacheable by anything between the browser and here.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Finding tutors")
public class TutorSearchController {

    private final TutorSearchService tutorSearchService;

    @GetMapping("/tutors")
    @Operation(summary = "Search tutors",
            description = "Published tutors matching every filter given, one page at a time. Featured "
                    + "tutors occupy up to three sponsored slots at the top of each page and carry "
                    + "featured=true. Facets give, for each filter value, how many tutors choosing it "
                    + "would return with the other filters unchanged. Reference values on the cards "
                    + "are rendered in the Accept-Language language.")
    public ApiResponse<TutorSearchResponse> searchTutors(@Valid @ParameterObject TutorSearchCriteria criteria,
                                                         Locale locale) {
        return ApiResponse.ok(tutorSearchService.search(criteria, Language.fromLocale(locale)));
    }
}
