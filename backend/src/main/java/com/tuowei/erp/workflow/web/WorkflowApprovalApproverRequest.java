package com.tuowei.erp.workflow.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkflowApprovalApproverRequest(
        @NotBlank(message = "approverType不能为空") String approverType,
        @NotNull(message = "approverId不能为空") Long approverId
) {
}
