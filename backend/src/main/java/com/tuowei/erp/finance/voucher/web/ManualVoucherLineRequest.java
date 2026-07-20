package com.tuowei.erp.finance.voucher.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 手工凭证分录行请求。每行借贷二选一（另一方为 0），由服务层校验。
 */
public record ManualVoucherLineRequest(
        @NotNull(message = "会计科目不能为空") Long subjectId,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String summary
) {
}
