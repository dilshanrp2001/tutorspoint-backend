package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminMetricsResponse;
import com.tutorspoint.admin.dto.MetricsWindow;

/** The pilot's numbers (OBJ-6). Administrators only. */
public interface AdminMetricsService {

    AdminMetricsResponse summary(MetricsWindow window);
}
