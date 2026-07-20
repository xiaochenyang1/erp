package com.tuowei.erp.finance.voucher.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ManualVoucherResponse(
        Long id,
        String voucherNo,
        LocalDate bizDate,
        BigDecimal amount,
        String status,
        String remark,
        Long postedVoucherId,
        Long reversalVoucherId,
        String rejectReason,
        String cancelReason,
        LocalDateTime submittedTime,
        LocalDateTime approvedTime,
        LocalDateTime postedTime,
        LocalDateTime cancelledTime,
        LocalDateTime createdTime,
        List<ManualVoucherLineResponse> lines
) {
}
