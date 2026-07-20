package com.tuowei.erp.issue.rule.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitPageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exception-rules")
public class ExceptionRuleController {

    private final ExceptionRuleService exceptionRuleService;

    public ExceptionRuleController(ExceptionRuleService exceptionRuleService) {
        this.exceptionRuleService = exceptionRuleService;
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ExceptionRuleResponse>> list(ExceptionRulePageQuery query) {
        return ApiResponse.success(exceptionRuleService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<ExceptionRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionRuleUpdateRequest request
    ) {
        return ApiResponse.success(exceptionRuleService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_MANAGE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ExceptionRuleResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(exceptionRuleService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_MANAGE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ExceptionRuleResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(exceptionRuleService.disable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_EXECUTE)
    @PostMapping("/{id}/scan")
    public ApiResponse<ExceptionRuleScanResultResponse> scan(@PathVariable Long id) {
        return ApiResponse.success(exceptionRuleService.scanRule(id));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_EXECUTE)
    @PostMapping("/scan-all")
    public ApiResponse<List<ExceptionRuleScanResultResponse>> scanAll() {
        return ApiResponse.success(exceptionRuleService.scanAll());
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_RULE_VIEW)
    @GetMapping("/hits")
    public ApiResponse<PageResponse<ExceptionRuleHitResponse>> hits(ExceptionRuleHitPageQuery query) {
        return ApiResponse.success(exceptionRuleService.listHits(query));
    }
}
