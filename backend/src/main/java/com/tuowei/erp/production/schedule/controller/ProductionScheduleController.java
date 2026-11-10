package com.tuowei.erp.production.schedule.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.production.schedule.service.ProductionScheduleService;
import com.tuowei.erp.production.schedule.web.ProductionScheduleRequest;
import com.tuowei.erp.production.schedule.web.ProductionScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/schedules")
public class ProductionScheduleController {
    private final ProductionScheduleService scheduleService;

    public ProductionScheduleController(ProductionScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_SCHEDULE_VIEW)
    @PostMapping("/preview")
    public ApiResponse<ProductionScheduleResponse> preview(
            @Valid @RequestBody ProductionScheduleRequest request
    ) {
        return ApiResponse.success(scheduleService.preview(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_SCHEDULE_RUN)
    @PostMapping("/run")
    public ApiResponse<ProductionScheduleResponse> run(
            @Valid @RequestBody ProductionScheduleRequest request
    ) {
        return ApiResponse.success(scheduleService.run(request));
    }
}
