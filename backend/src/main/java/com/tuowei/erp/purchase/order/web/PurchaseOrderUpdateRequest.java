package com.tuowei.erp.purchase.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderUpdateRequest(
        Long contractId,
        @NotNull(message = "supplierId不能为空") Long supplierId,
        @NotNull(message = "orderDate不能为空") LocalDate orderDate,
        LocalDate deliveryDate,
        String remark,
        @Valid @NotEmpty(message = "lines不能为空") List<PurchaseOrderLineRequest> lines
) {
    public PurchaseOrderUpdateRequest(Long supplierId, LocalDate orderDate, LocalDate deliveryDate,
                                      String remark, List<PurchaseOrderLineRequest> lines) {
        this(null, supplierId, orderDate, deliveryDate, remark, lines);
    }
}
