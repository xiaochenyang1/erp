package com.tuowei.erp.common.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商品辅单位换算：1 辅单位 = conversionFactor 库存单位。
 * 库存与金额始终按库存单位 qty 计算。
 */
public final class ProductAuxUnitConversion {

    private ProductAuxUnitConversion() {
    }

    public record ResolvedAuxUnit(
            BigDecimal stockQty,
            BigDecimal auxQty,
            String auxUnitName,
            BigDecimal conversionFactor
    ) {
    }

    public static ResolvedAuxUnit resolve(
            BigDecimal stockQty,
            BigDecimal auxQty,
            String auxUnitName,
            BigDecimal conversionFactor
    ) {
        String normalizedAuxUnit = normalizeText(auxUnitName);
        BigDecimal factor = conversionFactor;
        if (normalizedAuxUnit == null) {
            if (auxQty != null && auxQty.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("未启用辅单位时不能填写辅单位数量");
            }
            if (factor != null && factor.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("未启用辅单位时不能填写换算率");
            }
            BigDecimal qty = requirePositiveStockQty(stockQty);
            return new ResolvedAuxUnit(qty, null, null, null);
        }
        if (factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("启用辅单位时换算率必须大于0（1 辅单位 = N 库存单位）");
        }
        factor = factor.stripTrailingZeros();
        BigDecimal resolvedStockQty;
        BigDecimal resolvedAuxQty;
        if (auxQty != null && auxQty.compareTo(BigDecimal.ZERO) > 0) {
            resolvedAuxQty = ScalePrecision.quantity(auxQty);
            resolvedStockQty = ScalePrecision.quantity(resolvedAuxQty.multiply(factor));
        } else if (stockQty != null && stockQty.compareTo(BigDecimal.ZERO) > 0) {
            resolvedStockQty = ScalePrecision.quantity(stockQty);
            resolvedAuxQty = resolvedStockQty.divide(factor, 4, RoundingMode.HALF_UP);
        } else {
            throw new IllegalArgumentException("启用辅单位时必须填写辅单位数量或库存数量");
        }
        if (resolvedStockQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty必须大于0");
        }
        return new ResolvedAuxUnit(resolvedStockQty, resolvedAuxQty, normalizedAuxUnit, factor);
    }

    private static BigDecimal requirePositiveStockQty(BigDecimal stockQty) {
        if (stockQty == null || stockQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty必须大于0");
        }
        return ScalePrecision.quantity(stockQty);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
