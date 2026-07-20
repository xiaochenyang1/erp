package com.tuowei.erp.sales.returnorder.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesReturnLineRequest(
        @NotNull(message = "deliveryLineId不能为空") Long deliveryLineId,
        @NotNull(message = "qty不能为空")
        @DecimalMin(value = "0.0001", message = "qty必须大于0") BigDecimal qty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public SalesReturnLineRequest(Long deliveryLineId, BigDecimal qty, String remark) {
        this(deliveryLineId, qty, null, null, null, remark);
    }
}
