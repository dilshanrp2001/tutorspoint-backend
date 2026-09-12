package com.tutorspoint.auth;

import com.tutorspoint.auth.dto.ChildProfileRequest;
import com.tutorspoint.auth.dto.ChildProfileResponse;
import com.tutorspoint.common.exception.ResourceNotFoundException;

import java.util.List;

/**
 * A parent's child sub-profiles (FR-A5).
 *
 * <p>Like {@link AccountService}, no signature carries an owner: the parent is always the
 * caller, and a child is reached only through a query that already filters by that parent.
 * Somebody else's child id therefore behaves exactly like one that does not exist.
 */
public interface ChildProfileService {

    List<ChildProfileResponse> myChildren();

    ChildProfileResponse addChild(ChildProfileRequest request);

    /**
     * @throws ResourceNotFoundException if the id is unknown or belongs to another parent —
     *         the same answer either way, so the endpoint cannot be used to probe for ids
     */
    ChildProfileResponse updateChild(Long childId, ChildProfileRequest request);

    /** @throws ResourceNotFoundException as {@link #updateChild} does */
    void removeChild(Long childId);
}
