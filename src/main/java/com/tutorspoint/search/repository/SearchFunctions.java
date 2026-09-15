package com.tutorspoint.search.repository;

/** The database functions search calls, as created by {@code V7__tutor_search.sql}. */
final class SearchFunctions {

    /** {@code tutor_search_matches(document, keyword)}: does the profile mention every word, as a prefix. */
    static final String MATCHES = "tutor_search_matches";

    /** {@code tutor_search_rank(document, keyword)}: how well it does, for ordering. */
    static final String RANK = "tutor_search_rank";

    /** {@code great_circle_km(lat1, lon1, lat2, lon2)}: Haversine distance. */
    static final String GREAT_CIRCLE_KM = "great_circle_km";

    /** The {@code TutorProfile} attribute mapped to the trigger-maintained tsvector column. */
    static final String DOCUMENT_ATTRIBUTE = "searchDocument";

    private SearchFunctions() {
    }
}
