package com.tuowei.erp.workflow.web;

import jakarta.validation.constraints.Size;

public record WorkflowWithdrawRequest(
        @Size(max = 255, message = "撤回说明不能超过255个字符") String comment
) {
}
