package com.tuowei.erp.purchase.returnorder.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseReturnResponse(
        Long id,
        String returnNo,
        Long receiptId,
        String receiptNo,
        String orderNo,
        Long warehouseId,
        String warehouseName,
        LocalDate returnDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        List<PurchaseReturnLineResponse> lines
) {
}
