package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;

import java.math.BigDecimal;

/** Active cash allocations, excluding imported opening settlements. */
public record SettlementAllocationTotals(BigDecimal originalAmount, BigDecimal rawRelief, BigDecimal postedRelief) {
    public static BigDecimal postedSettled(BigDecimal storedPosted, BigDecimal settledOriginal,
                                          BigDecimal storedRaw, BigDecimal rate, SettlementAllocationTotals active) {
        if (storedPosted != null) return ScalePrecision.amount(storedPosted);
        BigDecimal settled = CurrencyAmountSupport.zero(settledOriginal);
        if (settled.signum() == 0) return ScalePrecision.amount(BigDecimal.ZERO);
        BigDecimal raw = storedRaw != null && storedRaw.signum() != 0
                ? storedRaw : CurrencyAmountSupport.base(settled, rate);
        if (active == null) return ScalePrecision.amount(raw);
        if (CurrencyAmountSupport.zero(active.originalAmount()).compareTo(settled) > 0) {
            throw new BusinessConflictException("核销明细超过子账已核销金额，请先核对历史记录");
        }
        // Legacy vouchers rounded each allocation separately. Keep their actual relief and
        // the imported opening balance; never silently re-round those posted vouchers.
        BigDecimal opening = raw.subtract(CurrencyAmountSupport.zero(active.rawRelief()));
        return ScalePrecision.amount(opening).add(CurrencyAmountSupport.zero(active.postedRelief()));
    }
}
