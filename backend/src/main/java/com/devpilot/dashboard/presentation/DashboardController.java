package com.devpilot.dashboard.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.dashboard.application.DashboardQueryService;
import com.devpilot.dashboard.application.DashboardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 집계 (docs/05 §13.1, BL-TDY-11). */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping
    @Operation(operationId = "dashboardGet")
    public DashboardView get(CurrentUser currentUser) {
        return dashboardQueryService.get(currentUser);
    }
}
