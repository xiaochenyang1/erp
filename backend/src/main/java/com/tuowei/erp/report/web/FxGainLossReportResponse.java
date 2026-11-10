package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已实现汇兑损益报表行：一笔收付款核销明细产生的汇兑差额。
 *
 * <p>新核销的资金分摊 − 账面冲减 = 汇兑损益 + 尾差调整。两项之和对应凭证 6061。
 * 收款为正是收益，付款为正是损失。历史行无过账快照时保留原折算记录并明确标记。
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
        BigDecimal fxGainLossAmount,
        BigDecimal roundingAmount,
        Boolean postingSnapshot
) {
    public FxGainLossReportResponse(Long allocationId, String direction, String settlementNo,
                                    LocalDate settlementDate, Long partnerId, String subledgerNo, String currencyCode,
                                    BigDecimal allocatedAmount, BigDecimal bookingRate, BigDecimal settlementRate,
                                    BigDecimal baseAllocatedAmount, BigDecimal baseSettledAmount, BigDecimal fxGainLossAmount) {
        this(allocationId, direction, settlementNo, settlementDate, partnerId, subledgerNo, currencyCode,
                allocatedAmount, bookingRate, settlementRate, baseAllocatedAmount, baseSettledAmount,
                fxGainLossAmount, BigDecimal.ZERO, false);
    }

    public BigDecimal totalGainLossAmount() { return fxGainLossAmount.add(roundingAmount); }
}
