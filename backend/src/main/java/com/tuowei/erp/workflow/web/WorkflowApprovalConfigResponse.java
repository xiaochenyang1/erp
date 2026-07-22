package com.tuowei.erp.workflow.web;

import java.util.List;

public record WorkflowApprovalConfigResponse(
        Long id,
        String businessType,
        String configName,
        String status,
        Integer taskTimeoutHours,
        String remark,
        List<WorkflowApprovalNodeResponse> nodes
) {
}
