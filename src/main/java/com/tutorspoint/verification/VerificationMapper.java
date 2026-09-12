package com.tutorspoint.verification;

import com.tutorspoint.verification.domain.VerificationDocument;
import com.tutorspoint.verification.dto.VerificationDocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * The one place a verification document becomes a response DTO.
 *
 * <p>{@code storageKey}, {@code reviewedBy} and the tutor are simply not mapped, and the build
 * cannot forget that: {@code unmappedTargetPolicy=ERROR} means a field added to the response is
 * a compile failure, while a field left behind on the entity is exactly what should happen to
 * an internal one.
 */
@Mapper
public interface VerificationMapper {

    @Mapping(target = "uploadedAt", source = "createdAt")
    VerificationDocumentResponse toResponse(VerificationDocument document);

    List<VerificationDocumentResponse> toResponses(List<VerificationDocument> documents);
}
