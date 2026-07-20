package com.tuowei.erp.workflow.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkflowApprovalConfigRequest(
        @NotBlank(message = "configName不能为空") String configName,
        String status,
        String remark,
        @NotEmpty(message = "审批节点不能为空") List<@Valid WorkflowApprovalNodeRequest> nodes
) {
}
