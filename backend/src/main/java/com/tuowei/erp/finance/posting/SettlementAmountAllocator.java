package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;

import java.math.BigDecimal;

/** Allocates a document's cents in request order, retaining conversions separately from posted movements. */
public final class SettlementAmountAllocator {
    private final BigDecimal expectedAllocated;
    private final BigDecimal expectedCash;
    private final BigDecimal settlementRate;
    private final BigDecimal advance;
    private BigDecimal cumulativeOriginal;
    private BigDecimal cumulativeFx = BigDecimal.ZERO;
    private BigDecimal allocated = BigDecimal.ZERO;
    private BigDecimal relief = BigDecimal.ZERO;
    private BigDecimal rounding = BigDecimal.ZERO;

    public SettlementAmountAllocator(BigDecimal total, BigDecimal allocated, BigDecimal rate) {
        this.expectedAllocated = ScalePrecision.amount(allocated);
        this.settlementRate = CurrencyAmountSupport.rate(rate);
        this.expectedCash = CurrencyAmountSupport.posting(total, rate);
        this.cumulativeOriginal = ScalePrecision.amount(total.subtract(allocated));
        if (expectedAllocated.signum() <= 0 || cumulativeOriginal.signum() < 0) {
            throw new IllegalArgumentException("核销金额必须大于0且不能超过收付款金额");
        }
        // Allocate the advance first so it retains its own settlement-rate conversion.
        this.advance = CurrencyAmountSupport.posting(cumulativeOriginal, rate);
    }

    public Allocation allocate(BigDecimal amount, BigDecimal bookingRate, BigDecimal original,
                               BigDecimal settled, BigDecimal baseOriginal, BigDecimal postedSettled) {
        BigDecimal value = ScalePrecision.amount(amount);
        BigDecimal settledBefore = ScalePrecision.amount(CurrencyAmountSupport.zero(settled));
        BigDecimal remaining = ScalePrecision.amount(original.subtract(settledBefore));
        if (value.signum() <= 0 || value.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("核销金额必须大于0且不能超过剩余金额");
        }
        BigDecimal carryingOriginal = CurrencyAmountSupport.baseAmountOrFallback(baseOriginal, original, bookingRate);
        BigDecimal carryingRemaining = carryingOriginal.subtract(postedSettled);
        if (postedSettled.signum() < 0 || carryingRemaining.signum() < 0) {
            throw new BusinessConflictException("本位币已核销金额超出账面金额，请先核对历史核销凭证");
        }
        // The final settlement clears exactly the remaining carrying balance. Earlier movements
        // follow cumulative rounding, even after an older settlement has been cancelled.
        BigDecimal postedRelief = value.compareTo(remaining) == 0 ? carryingRemaining
                : CurrencyAmountSupport.posting(settledBefore.add(value), bookingRate).subtract(postedSettled)
                        .max(BigDecimal.ZERO).min(carryingRemaining);
        BigDecimal rawCash = CurrencyAmountSupport.base(value, settlementRate);
        BigDecimal rawRelief = CurrencyAmountSupport.base(value, bookingRate);
        BigDecimal cashBefore = CurrencyAmountSupport.posting(cumulativeOriginal, settlementRate);
        cumulativeOriginal = cumulativeOriginal.add(value);
        BigDecimal postedCash = CurrencyAmountSupport.posting(cumulativeOriginal, settlementRate).subtract(cashBefore);
        BigDecimal fxBefore = ScalePrecision.amount(cumulativeFx);
        cumulativeFx = cumulativeFx.add(rawCash.subtract(rawRelief));
        BigDecimal postedFx = ScalePrecision.amount(cumulativeFx).subtract(fxBefore);
        BigDecimal tail = postedCash.subtract(postedRelief).subtract(postedFx);
        allocated = allocated.add(value);
        relief = relief.add(postedRelief);
        rounding = rounding.add(tail);
        return new Allocation(rawCash, rawRelief, postedCash, postedRelief, tail);
    }

    public SettlementPostingAmounts totals() {
        var result = new SettlementPostingAmounts(relief, advance, cumulativeFx, rounding);
        if (allocated.compareTo(expectedAllocated) != 0 || result.cashAmount().compareTo(expectedCash) != 0) {
            throw new IllegalStateException("核销明细与收付款过账金额不一致");
        }
        return result;
    }

    public record Allocation(BigDecimal rawCash, BigDecimal rawRelief, BigDecimal postedCash,
                             BigDecimal postedRelief, BigDecimal rounding) {
        public BigDecimal rawFx() { return rawCash.subtract(rawRelief); }
        public BigDecimal postedFx() { return postedCash.subtract(postedRelief).subtract(rounding); }
    }
}
