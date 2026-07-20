package com.tuowei.erp.purchase.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;

public final class PurchaseAmountCalculator {

    private PurchaseAmountCalculator() {
    }

    public static LineAmounts line(BigDecimal qty, BigDecimal price, BigDecimal taxRate) {
        BigDecimal normalizedQty = ScalePrecision.quantity(qty);
        BigDecimal normalizedPrice = ScalePrecision.amount(price);
        BigDecimal normalizedTaxRate = ScalePrecision.rate(taxRate);
        BigDecimal amount = ScalePrecision.amount(normalizedQty.multiply(normalizedPrice));
        BigDecimal taxAmount = ScalePrecision.taxAmount(amount, normalizedTaxRate);
        return new LineAmounts(normalizedQty, normalizedPrice, normalizedTaxRate, amount, taxAmount);
    }

    public record LineAmounts(
            BigDecimal qty,
            BigDecimal price,
            BigDecimal taxRate,
            BigDecimal amount,
            BigDecimal taxAmount
    ) {
    }

    public record DocumentTotals(
            BigDecimal totalQuantity,
            BigDecimal totalAmount,
            BigDecimal totalTaxAmount
    ) {

        public static DocumentTotals zero() {
            return new DocumentTotals(
                    ScalePrecision.quantity(BigDecimal.ZERO),
                    ScalePrecision.amount(BigDecimal.ZERO),
                    ScalePrecision.amount(BigDecimal.ZERO)
            );
        }

        public DocumentTotals add(LineAmounts line) {
            return add(line.qty(), line.amount(), line.taxAmount());
        }

        public DocumentTotals add(BigDecimal qty, BigDecimal amount, BigDecimal taxAmount) {
            return new DocumentTotals(
                    ScalePrecision.quantity(totalQuantity.add(ScalePrecision.quantity(qty))),
                    ScalePrecision.amount(totalAmount.add(ScalePrecision.amount(amount))),
                    ScalePrecision.amount(totalTaxAmount.add(ScalePrecision.amount(taxAmount)))
            );
        }
    }
}
