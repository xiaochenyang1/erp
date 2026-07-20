package com.tuowei.erp.common.document;

import java.util.List;

public record DocumentStateRuleResponse(
        String documentType,
        String documentName,
        String action,
        String actionName,
        String method,
        String path,
        String permission,
        List<String> allowedStatuses,
        List<String> allowedApprovalStatuses,
        String executionStatusField,
        List<String> allowedExecutionStatuses,
        List<String> blockedExecutionStatuses,
        String targetStatus,
        String targetApprovalStatus,
        String stateFailureMessage,
        String executionFailureMessage
) {
}
