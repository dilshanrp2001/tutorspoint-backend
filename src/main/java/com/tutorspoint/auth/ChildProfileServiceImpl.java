package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.dto.ChildProfileRequest;
import com.tutorspoint.auth.dto.ChildProfileResponse;
import com.tutorspoint.auth.repository.ChildProfileRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Child sub-profiles, owner-scoped by construction.
 *
 * <p>{@code hasRole('PARENT')} on the class is the role gate; the owner gate is the
 * repository, whose finders all take the parent id. Nothing here compares an owner by hand,
 * so there is no comparison to forget — the query either returns the caller's child or
 * returns nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
public class ChildProfileServiceImpl implements ChildProfileService {

    private final UserRepository users;
    private final ChildProfileRepository childProfiles;
    private final AccountMapper accountMapper;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public List<ChildProfileResponse> myChildren() {
        return accountMapper.toChildProfileResponses(
                childProfiles.findByParentIdOrderByNameAsc(currentUser.requireId()));
    }

    @Override
    @Transactional
    public ChildProfileResponse addChild(ChildProfileRequest request) {
        Parent parent = requireParent();
        ChildProfile child = parent.addChild(request.name(), request.grade(), request.examLevel(),
                request.school(), request.notes());
        // Flushed so the generated id is in the response rather than a null the client has
        // to re-fetch.
        users.saveAndFlush(parent);
        log.info("Parent {} added child profile {}", parent.getId(), child.getId());
        return accountMapper.toChildProfileResponse(child);
    }

    @Override
    @Transactional
    public ChildProfileResponse updateChild(Long childId, ChildProfileRequest request) {
        ChildProfile child = requireOwnChild(childId);
        child.updateDetails(request.name(), request.grade(), request.examLevel(),
                request.school(), request.notes());
        return accountMapper.toChildProfileResponse(child);
    }

    @Override
    @Transactional
    public void removeChild(Long childId) {
        Parent parent = requireParent();
        ChildProfile child = requireOwnChild(childId);
        // Through the aggregate root: the parent owns the collection, and orphanRemoval is
        // what actually deletes the row.
        parent.removeChild(child);
        users.save(parent);
        log.info("Parent {} removed child profile {}", parent.getId(), childId);
    }

    private Parent requireParent() {
        Long callerId = currentUser.requireId();
        return users.findById(callerId)
                .filter(Parent.class::isInstance)
                .map(Parent.class::cast)
                // Unreachable through the HTTP routes, which the role rule already guards.
                // Kept so a future non-HTTP caller fails loudly instead of casting blindly.
                .orElseThrow(() -> new UnauthorizedActionException(
                        "Account %s is not a parent account".formatted(callerId)));
    }

    /** Scoped to the caller, so another parent's child id is simply not found. */
    private ChildProfile requireOwnChild(Long childId) {
        return childProfiles.findByIdAndParentId(childId, currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Child profile", childId));
    }
}
