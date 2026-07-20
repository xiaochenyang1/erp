package com.tuowei.erp.production.operation.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionOperationReportRequest(
        @NotNull(message = "reportQty不能为空")
        @DecimalMin(value = "0.0001", message = "reportQty必须大于0") BigDecimal reportQty,
        @NotNull(message = "qualifiedQty不能为空")
        @DecimalMin(value = "0.0000", message = "qualifiedQty不能小于0") BigDecimal qualifiedQty,
        BigDecimal scrapQty,
        LocalDate reportDate,
        String remark
) {
}
