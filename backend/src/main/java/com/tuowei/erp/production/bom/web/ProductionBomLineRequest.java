package com.tuowei.erp.production.bom.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductionBomLineRequest(
        @NotNull Long materialProductId,
        @NotNull BigDecimal qtyPer,
        BigDecimal lossRate,
        String remark
) {
}
