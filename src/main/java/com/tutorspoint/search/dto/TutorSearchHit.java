package com.tutorspoint.search.dto;

import com.tutorspoint.tutor.dto.TutorCardDto;

/**
 * One search result: the tutor's card, and whether it occupies a sponsored slot.
 *
 * <p>{@code featured} is not decoration. FR-S6 requires sponsored results to be clearly
 * labelled, and a client can only label what the server tells it about - so the flag is on
 * every result, including the organic ones, rather than something a client infers from position.
 */
public record TutorSearchHit(

        TutorCardDto tutor,

        boolean featured) {
}
