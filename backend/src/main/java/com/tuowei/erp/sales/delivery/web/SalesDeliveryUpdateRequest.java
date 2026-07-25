package com.tuowei.erp.sales.delivery.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesDeliveryUpdateRequest(
        @NotNull(message = "orderId不能为空") Long orderId,
        @NotNull(message = "warehouseId不能为空") Long warehouseId,
        @NotNull(message = "deliveryDate不能为空") LocalDate deliveryDate,
        String remark,
        String carrierName,
        String trackingNo,
        @Valid @NotEmpty(message = "lines不能为空") List<SalesDeliveryLineRequest> lines
) {
}
