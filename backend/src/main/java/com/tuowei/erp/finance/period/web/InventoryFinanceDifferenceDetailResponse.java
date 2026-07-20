package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryFinanceDifferenceDetailResponse(
        Long periodId,
        String periodMonth,
        String sourceKey,
        String sourceType,
        String sourceNo,
        BigDecimal inventoryAmount,
        BigDecimal financeAmount,
        BigDecimal differenceAmount,
        String differenceType,
        List<InventoryTransactionResponse> inventoryTransactions,
        List<VoucherEntryResponse> voucherEntries
) {
    public record InventoryTransactionResponse(
            Long id,
            String bizType,
            String bizNo,
            String direction,
            BigDecimal qty,
            BigDecimal amount,
            LocalDateTime occurredTime,
            String remark
    ) {
    }

    public record VoucherEntryResponse(
            Long voucherId,
            String voucherNo,
            String sourceType,
            String sourceNo,
            LocalDate bizDate,
            Integer lineNo,
            String subjectCode,
            String subjectName,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String summary
    ) {
    }
}
