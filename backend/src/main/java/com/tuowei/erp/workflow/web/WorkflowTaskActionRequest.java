package com.tuowei.erp.workflow.web;

public record WorkflowTaskActionRequest(
        String comment,
        String reason
) {
    public String effectiveComment() {
        return comment != null ? comment : reason;
    }
}
