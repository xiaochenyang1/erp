package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收应付结算报表行。
 *
 * <p>{@code originalAmount}/{@code settledAmount}/{@code remainingAmount} 是**原币**金额，
 * {@code baseRemainingAmount} 是按子账入账汇率折算的本位币未结额，跨币种汇总只能用它。
 */
public record FinanceSettlementReportResponse(
        Long id,
        String direction,
        String bizNo,
        Long partnerId,
        LocalDate bizDate,
        String sourceType,
        String sourceNo,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String currencyCode,
        BigDecimal exchangeRate,
        BigDecimal baseRemainingAmount,
        String status
) {
}
