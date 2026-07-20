package com.tuowei.erp.workflow.web;

public record WorkflowApprovalApproverResponse(
        Long id,
        String approverType,
        Long approverId
) {
}
