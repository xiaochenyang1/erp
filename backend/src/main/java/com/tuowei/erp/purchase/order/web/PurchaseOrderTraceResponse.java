package com.tuowei.erp.purchase.order.web;

import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;

public record PurchaseOrderTraceResponse(
        PurchaseOrderResponse order,
        WorkflowApprovalInfoResponse approvalInfo,
        PurchaseOrderExecutionInfo executionInfo,
        PurchaseOrderRelatedDocs relatedDocs
) {
}
