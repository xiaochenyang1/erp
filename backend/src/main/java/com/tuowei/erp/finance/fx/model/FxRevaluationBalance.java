package com.tuowei.erp.finance.fx.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Current subledger snapshots plus the active settlements to remove when reconstructing period end. */
public record FxRevaluationBalance(
        String side,
        Long itemId,
        Long partnerId,
        LocalDate bizDate,
        String direction,
        String currencyCode,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal exchangeRate,
        BigDecimal baseOriginalAmount,
        BigDecimal baseSettledAmount,
        BigDecimal postedBaseSettledAmount,
        BigDecimal futureSettledAmount,
        BigDecimal futureBaseSettledAmount
) {
}
