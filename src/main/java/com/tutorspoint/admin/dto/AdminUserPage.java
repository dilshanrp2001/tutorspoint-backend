package com.tutorspoint.admin.dto;

import java.util.List;

/** A page of the account list. {@code page} is zero-based. */
public record AdminUserPage(List<AdminUserSummary> users, long total, int page, int size) {
}
