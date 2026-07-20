package com.tuowei.erp.purchase.receipt.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseReceiptResponse(
        Long id,
        String receiptNo,
        Long orderId,
        Long warehouseId,
        LocalDate receiptDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        List<PurchaseReceiptLineResponse> lines
) {
}
