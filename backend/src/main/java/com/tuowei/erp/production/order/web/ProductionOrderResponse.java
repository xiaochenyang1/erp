package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductionOrderResponse(
        Long id,
        String orderNo,
        Long bomId,
        Long productId,
        Long finishedWarehouseId,
        Long materialWarehouseId,
        BigDecimal plannedQty,
        BigDecimal completedQty,
        LocalDate plannedStartDate,
        LocalDate plannedFinishDate,
        String status,
        BigDecimal issuedAmount,
        BigDecimal finishedAmount,
        String remark,
        List<ProductionOrderMaterialResponse> materials
) {
}
