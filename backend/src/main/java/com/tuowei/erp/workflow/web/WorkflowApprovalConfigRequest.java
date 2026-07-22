package com.tuowei.erp.workflow.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record WorkflowApprovalConfigRequest(
        @NotBlank(message = "configName不能为空") String configName,
        String status,
        @Min(value = 1, message = "taskTimeoutHours不能小于1")
        @Max(value = 720, message = "taskTimeoutHours不能大于720") Integer taskTimeoutHours,
        String remark,
        @NotEmpty(message = "审批节点不能为空") List<@Valid WorkflowApprovalNodeRequest> nodes
) {
}
