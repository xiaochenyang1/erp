package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCompletionReversalRequest(
        LocalDate reversalDate,
        BigDecimal reversedQty,
        String remark
) {
    public ProductionCompletionReversalRequest(LocalDate reversalDate, String remark) {
        this(reversalDate, null, remark);
    }
}
