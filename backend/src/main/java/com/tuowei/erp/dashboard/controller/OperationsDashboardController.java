package com.tuowei.erp.dashboard.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.dashboard.service.OperationsDashboardService;
import com.tuowei.erp.dashboard.web.OperationsDashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class OperationsDashboardController {

    private final OperationsDashboardService operationsDashboardService;

    public OperationsDashboardController(OperationsDashboardService operationsDashboardService) {
        this.operationsDashboardService = operationsDashboardService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/operations")
    public ApiResponse<OperationsDashboardResponse> operations() {
        return ApiResponse.success(operationsDashboardService.getOperationsDashboard());
    }
}
