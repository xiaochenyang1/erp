package com.tuowei.erp.workflow.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkflowApprovalNodeRequest(
        @NotBlank(message = "nodeName不能为空") String nodeName,
        @Min(value = 1, message = "nodeOrder必须大于0") Integer nodeOrder,
        String approvalMode,
        @NotEmpty(message = "节点审批人不能为空") List<@Valid WorkflowApprovalApproverRequest> approvers
) {
}
