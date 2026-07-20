package com.tuowei.erp.report.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.report.service.BusinessTraceService;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import com.tuowei.erp.report.web.BusinessTraceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/business-traces")
public class BusinessTraceController {

    private final BusinessTraceService businessTraceService;

    public BusinessTraceController(BusinessTraceService businessTraceService) {
        this.businessTraceService = businessTraceService;
    }

    @PreAuthorize(PermissionCodes.HAS_REPORT_VIEW)
    @GetMapping
    public ApiResponse<BusinessTraceResponse> trace(BusinessTraceQuery query) {
        return ApiResponse.success(businessTraceService.trace(query));
    }
}
