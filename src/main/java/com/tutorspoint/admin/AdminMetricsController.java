package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminMetricsResponse;
import com.tutorspoint.admin.dto.MetricsWindow;
import com.tutorspoint.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The pilot's numbers (OBJ-6). {@code ROLE_ADMIN} only. */
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: metrics", description = "Registrations, searches, enquiries and the response rate")
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    @GetMapping
    @Operation(summary = "Metrics summary",
            description = "Registrations, searches and enquiries are counted over the days given (Sri Lanka "
                    + "time, both inclusive). Profile and review-queue counts are always as of now.")
    public ApiResponse<AdminMetricsResponse> summary(@Valid @ParameterObject MetricsWindow window) {
        return ApiResponse.ok(adminMetricsService.summary(window));
    }
}
