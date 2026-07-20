package com.tuowei.erp.sales.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesOrderUpdateRequest(
        @NotNull(message = "customerId不能为空") Long customerId,
        @NotNull(message = "warehouseId不能为空") Long warehouseId,
        @NotNull(message = "orderDate不能为空") LocalDate orderDate,
        LocalDate deliveryDate,
        String remark,
        @Valid @NotEmpty(message = "lines不能为空") List<SalesOrderLineRequest> lines
) {
}
