package com.tutorspoint.search.dto;

/** The limits a search request is held to, named once so the annotations and docs agree. */
public final class SearchValidation {

    /** Matches the {@code code} column of the reference tables. */
    public static final int CODE_MAX = 60;

    public static final int KEYWORD_MAX = 100;

    public static final int FEE_INTEGER_DIGITS = 8;
    public static final int FEE_FRACTION_DIGITS = 2;

    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * The page-size cap. A phone renders a handful of cards; a page of thousands is a scrape,
     * and every card it carries costs a query batch.
     */
    public static final int MAX_PAGE_SIZE = 50;

    /** Deep offsets make the database walk and discard every row before them; nobody reads page 500. */
    public static final int MAX_PAGE = 500;

    private SearchValidation() {
    }
}
