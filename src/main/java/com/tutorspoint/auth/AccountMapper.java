package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.ChildProfileResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * The one place an auth entity becomes a response DTO.
 *
 * <p>Generated, not hand-written, so a field added to {@code User} cannot silently go
 * missing from the response — and, more importantly here, a field added to a DTO cannot go
 * unmapped: the build sets {@code unmappedTargetPolicy=ERROR}, which turns that into a
 * compile failure rather than a null in an API payload.
 */
@Mapper
public interface AccountMapper {

    AccountResponse toAccountResponse(User user);

    ChildProfileResponse toChildProfileResponse(ChildProfile child);

    List<ChildProfileResponse> toChildProfileResponses(List<ChildProfile> children);
}
