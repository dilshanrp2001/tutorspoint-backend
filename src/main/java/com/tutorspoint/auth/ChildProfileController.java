package com.tutorspoint.auth;

import com.tutorspoint.auth.dto.ChildProfileRequest;
import com.tutorspoint.auth.dto.ChildProfileResponse;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A parent's child sub-profiles (FR-A5).
 *
 * <p>Nested under {@code /api/account} because a child is part of the caller's own account,
 * not a resource in its own right. The child id in the path is scoped to the caller by the
 * service, so another parent's id reads as "not found".
 */
@RestController
@RequestMapping("/api/account/children")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Child profiles", description = "Sub-profiles for the children a parent manages")
public class ChildProfileController {

    private final ChildProfileService childProfileService;

    @GetMapping
    @Operation(summary = "List my children")
    public ApiResponse<List<ChildProfileResponse>> myChildren() {
        return ApiResponse.ok(childProfileService.myChildren());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a child")
    public ApiResponse<ChildProfileResponse> addChild(@Valid @RequestBody ChildProfileRequest request) {
        return ApiResponse.ok(childProfileService.addChild(request));
    }

    @PutMapping("/{childId}")
    @Operation(summary = "Edit one of my children")
    public ApiResponse<ChildProfileResponse> updateChild(@PathVariable Long childId,
                                                        @Valid @RequestBody ChildProfileRequest request) {
        return ApiResponse.ok(childProfileService.updateChild(childId, request));
    }

    @DeleteMapping("/{childId}")
    @Operation(summary = "Remove one of my children")
    public ApiResponse<Void> removeChild(@PathVariable Long childId) {
        childProfileService.removeChild(childId);
        return ApiResponse.ok();
    }
}
