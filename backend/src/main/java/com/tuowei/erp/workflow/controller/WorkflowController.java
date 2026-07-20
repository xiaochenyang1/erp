package com.tuowei.erp.workflow.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.service.WorkflowTaskActionService;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskActionRequest;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskTransferRequest;
import com.tuowei.erp.workflow.web.WorkflowWithdrawRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowTaskActionService workflowTaskActionService;

    public WorkflowController(
            WorkflowService workflowService,
            WorkflowTaskActionService workflowTaskActionService
    ) {
        this.workflowService = workflowService;
        this.workflowTaskActionService = workflowTaskActionService;
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_VIEW)
    @GetMapping("/api/workflow/tasks")
    public ApiResponse<PageResponse<WorkflowTaskResponse>> listTasks(WorkflowTaskPageQuery query) {
        return ApiResponse.success(workflowService.listTasks(query));
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_VIEW)
    @GetMapping("/api/workflow/tasks/{id}")
    public ApiResponse<WorkflowTaskResponse> taskDetail(@PathVariable Long id) {
        return ApiResponse.success(workflowService.taskDetail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_APPROVE)
    @PostMapping("/api/workflow/tasks/{id}/approve")
    public ApiResponse<Void> approveTask(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) WorkflowTaskActionRequest request
    ) {
        workflowTaskActionService.approve(id, request == null ? null : request.effectiveComment());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_APPROVE)
    @PostMapping("/api/workflow/tasks/{id}/transfer")
    public ApiResponse<WorkflowTaskResponse> transferTask(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowTaskTransferRequest request
    ) {
        return ApiResponse.success(workflowService.transfer(id, request.targetUserId(), request.comment()));
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_REJECT)
    @PostMapping("/api/workflow/tasks/{id}/reject")
    public ApiResponse<Void> rejectTask(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) WorkflowTaskActionRequest request
    ) {
        workflowTaskActionService.reject(id, request == null ? null : request.effectiveComment());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_VIEW)
    @GetMapping("/api/workflow/records")
    public ApiResponse<PageResponse<WorkflowRecordResponse>> listRecords(WorkflowRecordPageQuery query) {
        return ApiResponse.success(workflowService.listRecords(query));
    }

    @PreAuthorize(PermissionCodes.HAS_WORKFLOW_WITHDRAW)
    @PostMapping("/api/workflow/{businessType}/{businessId}/withdraw")
    public ApiResponse<Void> withdraw(
            @PathVariable String businessType,
            @PathVariable Long businessId,
            @Valid @RequestBody(required = false) WorkflowWithdrawRequest request
    ) {
        WorkflowWithdrawRequest safeRequest = request == null ? new WorkflowWithdrawRequest(null) : request;
        workflowService.withdraw(businessType, businessId, safeRequest.comment());
        return ApiResponse.success(null);
    }
}
