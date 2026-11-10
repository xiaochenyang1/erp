package com.tuowei.erp.production.cost.service;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure allocation rules shared by completion and completion reversal. */
public final class ProductionCostAllocation {
    private ProductionCostAllocation() {
    }

    public static ProductionCostAmounts allocateCompletion(
            BigDecimal availableMaterial,
            BigDecimal availableLabor,
            BigDecimal availableOverhead,
            BigDecimal remainingCompletableQty,
            BigDecimal completionQty
    ) {
        BigDecimal remaining = ScalePrecision.quantity(remainingCompletableQty);
        BigDecimal completed = ScalePrecision.quantity(completionQty);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("没有可结转的生产成本，不能完工");
        }
        if (completed.compareTo(remaining) == 0) {
            return new ProductionCostAmounts(availableMaterial, availableLabor, availableOverhead);
        }
        return new ProductionCostAmounts(
                proportional(availableMaterial, completed, remaining),
                proportional(availableLabor, completed, remaining),
                proportional(availableOverhead, completed, remaining)
        );
    }

    public static ProductionCostAmounts splitReversal(
            BigDecimal reversalAmount,
            BigDecimal finishedMaterial,
            BigDecimal finishedLabor,
            BigDecimal finishedOverhead
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(reversalAmount));
        BigDecimal materialBasis = ScalePrecision.zeroDefault(finishedMaterial).abs();
        BigDecimal laborBasis = ScalePrecision.zeroDefault(finishedLabor).abs();
        BigDecimal overheadBasis = ScalePrecision.zeroDefault(finishedOverhead).abs();
        BigDecimal basis = materialBasis.add(laborBasis).add(overheadBasis);
        if (basis.compareTo(BigDecimal.ZERO) == 0) {
            return new ProductionCostAmounts(amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal material = proportional(amount, materialBasis, basis);
        if (overheadBasis.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal labor = proportional(amount, laborBasis, basis);
            return new ProductionCostAmounts(material, labor, amount.subtract(material).subtract(labor));
        }
        if (laborBasis.compareTo(BigDecimal.ZERO) > 0) {
            return new ProductionCostAmounts(material, amount.subtract(material), BigDecimal.ZERO);
        }
        return new ProductionCostAmounts(amount, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal proportional(BigDecimal amount, BigDecimal numerator, BigDecimal denominator) {
        BigDecimal safeAmount = ScalePrecision.zeroDefault(amount);
        if (safeAmount.compareTo(BigDecimal.ZERO) == 0 || numerator.compareTo(BigDecimal.ZERO) == 0) {
            return ScalePrecision.amount(BigDecimal.ZERO);
        }
        return ScalePrecision.amount(safeAmount.multiply(numerator).divide(denominator, 8, RoundingMode.HALF_UP));
    }
}
