package com.tutorspoint.admin.dto;

import java.util.List;

/**
 * Everything the tutor review screen shows, in one response.
 *
 * @param account   the tutor's account
 * @param profile   their profile, or null when they have never opened the wizard
 * @param documents their submitted documents, newest first
 */
public record AdminTutorDetail(
        AdminUserSummary account,
        AdminTutorProfileView profile,
        List<AdminDocumentResponse> documents) {
}
