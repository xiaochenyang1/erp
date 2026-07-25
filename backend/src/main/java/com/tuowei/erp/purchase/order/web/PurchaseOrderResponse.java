package com.tuowei.erp.purchase.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        String status,
        String approvalStatus,
        String receiptStatus,
        Long sourceInquiryId,
        String sourceInquiryNo,
        Long sourceQuoteId,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        List<PurchaseOrderLineResponse> lines
) {
}
