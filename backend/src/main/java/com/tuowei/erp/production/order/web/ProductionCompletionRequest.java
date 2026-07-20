package com.tuowei.erp.production.order.web;

import java.time.LocalDate;
import java.math.BigDecimal;

public record ProductionCompletionRequest(
        LocalDate completionDate,
        BigDecimal completedQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public ProductionCompletionRequest(LocalDate completionDate, BigDecimal completedQty, String remark) {
        this(completionDate, completedQty, null, null, null, remark);
    }

    public ProductionCompletionRequest(LocalDate completionDate, String remark) {
        this(completionDate, null, null, null, null, remark);
    }
}
