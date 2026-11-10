package com.tuowei.erp.production.cost.service;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;

/** Immutable material/labor/overhead amount tuple. */
public record ProductionCostAmounts(
        BigDecimal material,
        BigDecimal labor,
        BigDecimal overhead
) {
    public ProductionCostAmounts {
        material = ScalePrecision.amount(ScalePrecision.zeroDefault(material));
        labor = ScalePrecision.amount(ScalePrecision.zeroDefault(labor));
        overhead = ScalePrecision.amount(ScalePrecision.zeroDefault(overhead));
    }

    public BigDecimal total() {
        return ScalePrecision.amount(material.add(labor).add(overhead));
    }
}
