package com.tutorspoint.reference;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.reference.dto.DistrictResponse;
import com.tutorspoint.reference.dto.ReferenceDataResponse;
import com.tutorspoint.reference.dto.ReferenceItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * The vocabulary of the platform: the subject, level, syllabus and area lists a client
 * builds every filter and every profile form from.
 *
 * <p>Public and read-only. A guest searching for a tutor has to be able to draw the search
 * form before signing in, so these routes are open — they expose nothing but the
 * translated contents of tables that ship with the application.
 *
 * <p>Spring resolves {@code Accept-Language} into the {@link Locale} injected below; this
 * controller is the only place that translation from an HTTP header to a
 * {@link Language} happens, which is what keeps the service free of HTTP concerns. An
 * absent or unsupported header is not an error — {@link Language#fromLocale} answers in
 * English.
 */
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
@Tag(name = "Reference data", description = "Subjects, exam levels, syllabuses and areas, in the caller's language")
public class ReferenceController {

    private final ReferenceDataService referenceData;

    @GetMapping
    @Operation(summary = "Every reference list in one response",
            description = "What a search or filter screen needs to render itself in a single request.")
    public ApiResponse<ReferenceDataResponse> all(Locale locale) {
        return ApiResponse.ok(referenceData.all(Language.fromLocale(locale)));
    }

    @GetMapping("/subjects")
    @Operation(summary = "Subjects offered on the platform")
    public ApiResponse<List<ReferenceItemResponse>> subjects(Locale locale) {
        return ApiResponse.ok(referenceData.subjects(Language.fromLocale(locale)));
    }

    @GetMapping("/exam-levels")
    @Operation(summary = "Exam levels, from Grade 5 Scholarship to professional")
    public ApiResponse<List<ReferenceItemResponse>> examLevels(Locale locale) {
        return ApiResponse.ok(referenceData.examLevels(Language.fromLocale(locale)));
    }

    @GetMapping("/syllabuses")
    @Operation(summary = "Syllabuses: national by medium, Cambridge, Edexcel, professional")
    public ApiResponse<List<ReferenceItemResponse>> syllabuses(Locale locale) {
        return ApiResponse.ok(referenceData.syllabuses(Language.fromLocale(locale)));
    }

    @GetMapping("/areas")
    @Operation(summary = "Districts with their towns",
            description = "Nested two levels deep, each town carrying the coordinates used for distance sorting.")
    public ApiResponse<List<DistrictResponse>> areas(Locale locale) {
        return ApiResponse.ok(referenceData.districts(Language.fromLocale(locale)));
    }

    @GetMapping("/mediums")
    @Operation(summary = "Languages a class can be taught in")
    public ApiResponse<List<ReferenceItemResponse>> mediums(Locale locale) {
        return ApiResponse.ok(referenceData.mediums(Language.fromLocale(locale)));
    }

    @GetMapping("/class-formats")
    @Operation(summary = "Ways a class can be delivered")
    public ApiResponse<List<ReferenceItemResponse>> classFormats(Locale locale) {
        return ApiResponse.ok(referenceData.classFormats(Language.fromLocale(locale)));
    }
}
