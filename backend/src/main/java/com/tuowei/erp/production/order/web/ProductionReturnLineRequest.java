package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionReturnLineRequest(
        Long orderMaterialId,
        BigDecimal returnQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public ProductionReturnLineRequest(Long orderMaterialId, BigDecimal returnQty, String remark) {
        this(orderMaterialId, returnQty, null, null, null, remark);
    }
}
