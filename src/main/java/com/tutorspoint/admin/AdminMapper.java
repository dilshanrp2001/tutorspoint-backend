package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminDocumentResponse;
import com.tutorspoint.admin.dto.AdminTutorProfileView;
import com.tutorspoint.admin.dto.AdminUserSummary;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.verification.domain.VerificationDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * The one place an account, a profile or a document becomes an admin response DTO.
 *
 * <p>The admin views show more than the owner's own views do - a reviewer's name, a tutor's
 * phone number - and still never the password hash, a storage key or token state. Those are
 * simply not target fields, and {@code unmappedTargetPolicy=ERROR} means a field added to a
 * response is a compile failure until somebody decides where it comes from.
 */
@Mapper
public interface AdminMapper {

    @Mapping(target = "registeredAt", source = "createdAt")
    AdminUserSummary toSummary(User user);

    @Mapping(target = "missingFields", expression = "java(profile.missingFields())")
    AdminTutorProfileView toProfileView(TutorProfile profile);

    @Mapping(target = "reviewedByName", source = "reviewedBy.fullName")
    @Mapping(target = "uploadedAt", source = "createdAt")
    AdminDocumentResponse toDocument(VerificationDocument document);

    List<AdminDocumentResponse> toDocuments(List<VerificationDocument> documents);
}
