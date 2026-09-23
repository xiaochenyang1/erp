package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已实现汇兑损益报表行：一笔收付款核销明细产生的汇兑差额。
 *
 * <p>{@code fxGainLossAmount} = {@code baseAllocatedAmount}（按结算汇率）− {@code baseSettledAmount}
 * （按子账入账汇率）。收款为正是汇兑收益，付款为正是汇兑损失，与凭证的 6061 腿方向一致。
 */
public record FxGainLossReportResponse(
        Long allocationId,
        String direction,
        String settlementNo,
        LocalDate settlementDate,
        Long partnerId,
        String subledgerNo,
        String currencyCode,
        BigDecimal allocatedAmount,
        BigDecimal bookingRate,
        BigDecimal settlementRate,
        BigDecimal baseAllocatedAmount,
        BigDecimal baseSettledAmount,
        BigDecimal fxGainLossAmount
) {
}
