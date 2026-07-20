package com.tuowei.erp.workflow.web;

import java.util.List;

public record WorkflowApprovalConfigResponse(
        Long id,
        String businessType,
        String configName,
        String status,
        String remark,
        List<WorkflowApprovalNodeResponse> nodes
) {
}
