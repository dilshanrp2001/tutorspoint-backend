package com.tutorspoint.search.dto;

import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.search.ranking.DistanceRanking;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * A tutor search, as the query string of {@code GET /api/search/tutors} states it (FR-S1,
 * FR-S2, FR-S7).
 *
 * <p><strong>Every filter is optional, and an absent one filters nothing.</strong> A search
 * with no parameters at all is legitimate - it is the browse page - and each parameter a
 * parent adds narrows it by exactly one condition.
 *
 * <p>Reference values are codes, the same codes {@code /api/reference} serves. A code that
 * names nothing is not an error: it matches no tutors, which is the true answer.
 *
 * <p>The compact constructor normalises before validation runs: blanks become absent, codes
 * are upper-cased, the sort key lower-cased, and paging gets its defaults. So a validation
 * message is always about the value search would actually have used.
 *
 * @param availableOnline only {@code true} filters; false or absent means "either"
 * @param verifiedOnly    only {@code true} filters; false or absent means "either"
 * @param minFee          with {@code maxFee}, a price band: a tutor matches when their fee range
 *                        overlaps it
 */
public record TutorSearchCriteria(

        @Parameter(description = "Subject code, e.g. CHEMISTRY")
        @Size(max = SearchValidation.CODE_MAX, message = "{validation.search.code.size}")
        String subject,

        @Parameter(description = "Exam level code, e.g. GCE_AL")
        @Size(max = SearchValidation.CODE_MAX, message = "{validation.search.code.size}")
        String examLevel,

        @Parameter(description = "Syllabus code, e.g. CAMBRIDGE")
        @Size(max = SearchValidation.CODE_MAX, message = "{validation.search.code.size}")
        String syllabus,

        Medium medium,

        ClassFormat classFormat,

        @Parameter(description = "Area code - a district or a town. A district also matches tutors "
                + "serving any of its towns, and a town also matches tutors serving its whole district. "
                + "Required for distance sorting, which measures from this area's centre.")
        @Size(max = SearchValidation.CODE_MAX, message = "{validation.search.code.size}")
        String area,

        @Parameter(description = "Lower end of the price band, in rupees")
        @PositiveOrZero(message = "{validation.search.fee.range}")
        @Digits(integer = SearchValidation.FEE_INTEGER_DIGITS, fraction = SearchValidation.FEE_FRACTION_DIGITS,
                message = "{validation.search.fee.range}")
        BigDecimal minFee,

        @Parameter(description = "Upper end of the price band, in rupees")
        @PositiveOrZero(message = "{validation.search.fee.range}")
        @Digits(integer = SearchValidation.FEE_INTEGER_DIGITS, fraction = SearchValidation.FEE_FRACTION_DIGITS,
                message = "{validation.search.fee.range}")
        BigDecimal maxFee,

        @Parameter(description = "true for tutors who teach online")
        Boolean availableOnline,

        @Parameter(description = "true for verified tutors only")
        Boolean verifiedOnly,

        @Parameter(description = "Minimum average rating, 0 to 5. Unreviewed tutors do not match.")
        @DecimalMin(value = "0", message = "{validation.search.rating.range}")
        @DecimalMax(value = "5", message = "{validation.search.rating.range}")
        BigDecimal minRating,

        @Parameter(description = "Free text over headline, bio and subject names, in any of the three "
                + "languages. Each word matches as a prefix.")
        @Size(max = SearchValidation.KEYWORD_MAX, message = "{validation.search.keyword.size}")
        String keyword,

        @Parameter(description = "relevance (default), price_asc, rating_desc, experience_desc or distance_asc")
        @SupportedSort
        String sort,

        @Parameter(description = "Zero-based page number")
        @PositiveOrZero(message = "{validation.search.page.range}")
        @Max(value = SearchValidation.MAX_PAGE, message = "{validation.search.page.range}")
        Integer page,

        @Parameter(description = "Results per page, 1 to " + SearchValidation.MAX_PAGE_SIZE)
        @Min(value = 1, message = "{validation.search.size.range}")
        @Max(value = SearchValidation.MAX_PAGE_SIZE, message = "{validation.search.size.range}")
        Integer size) {

    public TutorSearchCriteria {
        subject = code(subject);
        examLevel = code(examLevel);
        syllabus = code(syllabus);
        area = code(area);
        keyword = trimToNull(keyword);
        sort = sort == null || sort.isBlank() ? null : sort.trim().toLowerCase(Locale.ROOT);
        page = page == null ? 0 : page;
        size = size == null ? SearchValidation.DEFAULT_PAGE_SIZE : size;
    }

    /** No filters, the default ordering, the first page. */
    public static TutorSearchCriteria unfiltered() {
        return new TutorSearchCriteria(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    @AssertTrue(message = "{validation.search.fee.order}")
    @Parameter(hidden = true)
    public boolean isFeeBandOrdered() {
        return minFee == null || maxFee == null || minFee.compareTo(maxFee) <= 0;
    }

    /** Distance from where? The area is the only answer the request can give. */
    @AssertTrue(message = "{validation.search.distance.area-required}")
    @Parameter(hidden = true)
    public boolean isDistanceSortAnchored() {
        return !DistanceRanking.KEY.equals(sort) || area != null;
    }

    public boolean onlineOnly() {
        return Boolean.TRUE.equals(availableOnline);
    }

    public boolean verifiedOnlyRequested() {
        return Boolean.TRUE.equals(verifiedOnly);
    }

    private static String code(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
