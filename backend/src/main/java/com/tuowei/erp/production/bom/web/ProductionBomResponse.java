package com.tuowei.erp.production.bom.web;

import java.math.BigDecimal;
import java.util.List;

public record ProductionBomResponse(
        Long id,
        String bomNo,
        Long productId,
        BigDecimal baseQty,
        String status,
        String remark,
        List<ProductionBomLineResponse> lines
) {
}
