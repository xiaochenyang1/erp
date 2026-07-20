package com.tuowei.erp.sales.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;

public final class SalesAmountCalculator {

    private SalesAmountCalculator() {
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
            return new DocumentTotals(
                    ScalePrecision.quantity(totalQuantity.add(line.qty())),
                    ScalePrecision.amount(totalAmount.add(line.amount())),
                    ScalePrecision.amount(totalTaxAmount.add(line.taxAmount()))
            );
        }
    }
}
