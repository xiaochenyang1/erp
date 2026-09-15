package com.tuowei.erp.finance.currency.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Shared currency snapshot and conversion rules for persisted business documents. */
public final class CurrencyAmountSupport {

    public static final String LEGACY_CURRENCY = "CNY";
    public static final BigDecimal ONE = BigDecimal.ONE;

    private CurrencyAmountSupport() {
    }

    public static String currency(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY_CURRENCY;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static BigDecimal rate(BigDecimal value) {
        if (value == null) {
            return ONE;
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("汇率必须大于0");
        }
        return value;
    }

    public static BigDecimal base(BigDecimal amount, BigDecimal exchangeRate) {
        return zero(amount).multiply(rate(exchangeRate)).setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal posting(BigDecimal amount, BigDecimal exchangeRate) {
        return ScalePrecision.amount(base(amount, exchangeRate));
    }

    public static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
