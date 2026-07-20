package com.tuowei.erp.purchase.returnorder.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseReturnLineRequest(
        @NotNull(message = "receiptLineId不能为空") Long receiptLineId,
        @NotNull(message = "qty不能为空")
        @DecimalMin(value = "0.0001", message = "qty必须大于0") BigDecimal qty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public PurchaseReturnLineRequest(Long receiptLineId, BigDecimal qty, String remark) {
        this(receiptLineId, qty, null, null, null, remark);
    }
}
