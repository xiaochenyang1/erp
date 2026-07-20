package com.tuowei.erp.purchase.order.web;

import java.util.List;

public record PurchaseOrderRelatedDocs(
        List<PurchaseOrderDocumentSummary> receipts,
        List<PurchaseOrderDocumentSummary> returns,
        List<PurchaseOrderDocumentSummary> payables,
        List<PurchaseOrderDocumentSummary> payments,
        List<PurchaseOrderDocumentSummary> vouchers
) {
}
