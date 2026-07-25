package com.tuowei.erp.sales.delivery.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDeliveryLineRequest(
        @NotNull(message = "orderLineId不能为空") Long orderLineId,
        @NotNull(message = "qty不能为空")
        @DecimalMin(value = "0.0001", message = "qty必须大于0") BigDecimal qty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        Long locationId,
        String serialNos,
        String remark
) {
}
