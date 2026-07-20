package com.tuowei.erp.issue.sla.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exception-sla-policies")
public class ExceptionSlaPolicyController {

    private final ExceptionSlaPolicyService exceptionSlaPolicyService;

    public ExceptionSlaPolicyController(ExceptionSlaPolicyService exceptionSlaPolicyService) {
        this.exceptionSlaPolicyService = exceptionSlaPolicyService;
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_SLA_POLICY_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ExceptionSlaPolicyResponse>> list(ExceptionSlaPolicyPageQuery query) {
        return ApiResponse.success(exceptionSlaPolicyService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_SLA_POLICY_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<ExceptionSlaPolicyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionSlaPolicyUpdateRequest request
    ) {
        return ApiResponse.success(exceptionSlaPolicyService.update(id, request));
    }
}
