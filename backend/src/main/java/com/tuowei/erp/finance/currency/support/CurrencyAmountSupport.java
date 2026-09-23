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

    /**
     * Returns a persisted base-currency amount when it exists, otherwise converts
     * the legacy/original amount with the document exchange-rate snapshot.
     *
     * <p>V161 introduced base columns with a zero default for historical rows.
     * Treating a zero stored value as authoritative would therefore erase those
     * rows from exposure and report totals. This helper keeps old rows readable
     * while preserving the immutable base snapshot on new rows.</p>
     */
    public static BigDecimal baseAmountOrFallback(
            BigDecimal storedBaseAmount,
            BigDecimal originalAmount,
            BigDecimal exchangeRate
    ) {
        return ScalePrecision.amount(baseSnapshotOrConversion(storedBaseAmount, originalAmount, exchangeRate));
    }

    /**
     * Calculates an open-item balance in base currency. The original and settled
     * amounts remain in transaction currency; when the base snapshots are
     * present, they are preferred so realized FX settlements do not change the
     * carrying value of the remaining balance.
     */
    public static BigDecimal baseRemaining(
            BigDecimal originalAmount,
            BigDecimal settledAmount,
            BigDecimal baseOriginalAmount,
            BigDecimal baseSettledAmount,
            BigDecimal exchangeRate
    ) {
        BigDecimal baseOriginal = baseAmountOrFallback(baseOriginalAmount, originalAmount, exchangeRate);
        BigDecimal settled = zero(settledAmount);
        BigDecimal baseSettled = settled.signum() == 0
                ? ScalePrecision.amount(BigDecimal.ZERO)
                : baseAmountOrFallback(baseSettledAmount, settled, exchangeRate);
        return ScalePrecision.amount(baseOriginal.subtract(baseSettled));
    }

    /** Calculates a document's tax-inclusive total in base currency. */
    public static BigDecimal baseDocumentTotal(
            BigDecimal originalAmount,
            BigDecimal originalTaxAmount,
            BigDecimal baseAmount,
            BigDecimal baseTaxAmount,
            BigDecimal exchangeRate
    ) {
        return ScalePrecision.amount(
                baseSnapshotOrConversion(baseAmount, originalAmount, exchangeRate)
                        .add(baseSnapshotOrConversion(baseTaxAmount, originalTaxAmount, exchangeRate))
        );
    }

    private static BigDecimal baseSnapshotOrConversion(
            BigDecimal storedBaseAmount,
            BigDecimal originalAmount,
            BigDecimal exchangeRate
    ) {
        if (storedBaseAmount != null && storedBaseAmount.signum() != 0) {
            return storedBaseAmount;
        }
        return base(originalAmount, exchangeRate);
    }

    public static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
