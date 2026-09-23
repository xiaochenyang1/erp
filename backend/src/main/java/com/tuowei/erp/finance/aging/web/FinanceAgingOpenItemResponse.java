package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账龄未结清明细。
 *
 * <p>{@code remainingAmount} 是**原币**未结额，{@code baseRemainingAmount} 是按子账入账汇率
 * 折算的本位币未结额。汇总口径一律用后者。
 */
public record FinanceAgingOpenItemResponse(
        String side,
        Long id,
        String docNo,
        Long partnerId,
        String partnerName,
        LocalDate bizDate,
        LocalDate dueDate,
        long agingDays,
        String bucketCode,
        BigDecimal remainingAmount,
        String currencyCode,
        BigDecimal exchangeRate,
        BigDecimal baseRemainingAmount,
        String status
) {
}
