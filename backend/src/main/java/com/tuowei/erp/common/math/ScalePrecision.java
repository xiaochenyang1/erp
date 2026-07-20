package com.tuowei.erp.common.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ScalePrecision {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private ScalePrecision() {
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal safeQuantity(BigDecimal value) {
        if (value == null) {
            return quantity(BigDecimal.ZERO);
        }
        return quantity(value);
    }

    public static BigDecimal zeroDefault(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal taxAmount(BigDecimal amount, BigDecimal taxRate) {
        return amount(amount.multiply(taxRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public static BigDecimal unitCost(BigDecimal amount, BigDecimal qty) {
        return amount.divide(qty, 4, RoundingMode.HALF_UP);
    }
}
