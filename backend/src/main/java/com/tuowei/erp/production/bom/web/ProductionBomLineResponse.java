package com.tuowei.erp.production.bom.web;

import java.math.BigDecimal;

public record ProductionBomLineResponse(
        Long id,
        Integer lineNo,
        Long materialProductId,
        BigDecimal qtyPer,
        BigDecimal lossRate,
        String remark
) {
}
