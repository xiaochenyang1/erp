package com.tuowei.erp.workflow.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/configs")
public class WorkflowApprovalConfigController {

    private final WorkflowApprovalConfigService configService;

    public WorkflowApprovalConfigController(WorkflowApprovalConfigService configService) {
        this.configService = configService;
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_CONFIG_VIEW)
    @GetMapping("/{businessType}")
    public ApiResponse<WorkflowApprovalConfigResponse> detail(@PathVariable String businessType) {
        return ApiResponse.success(configService.getByBusinessType(businessType));
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_CONFIG_UPDATE)
    @PutMapping("/{businessType}")
    public ApiResponse<WorkflowApprovalConfigResponse> save(
            @PathVariable String businessType,
            @Valid @RequestBody WorkflowApprovalConfigRequest request
    ) {
        return ApiResponse.success(configService.save(businessType, request));
    }
}
