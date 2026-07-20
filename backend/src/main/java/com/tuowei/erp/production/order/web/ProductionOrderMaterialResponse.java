package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;

public record ProductionOrderMaterialResponse(
        Long id,
        Integer lineNo,
        Long materialProductId,
        BigDecimal requiredQty,
        BigDecimal issuedQty,
        BigDecimal issuedAmount,
        String remark
) {
}
