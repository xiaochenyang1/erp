package com.tuowei.erp.workflow.web;

import java.util.List;

public record WorkflowApprovalNodeResponse(
        Long id,
        String nodeName,
        Integer nodeOrder,
        String approvalMode,
        String status,
        List<WorkflowApprovalApproverResponse> approvers
) {
}
