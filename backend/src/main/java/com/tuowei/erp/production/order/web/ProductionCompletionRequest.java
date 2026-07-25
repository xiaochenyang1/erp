package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCompletionRequest(
        LocalDate completionDate,
        BigDecimal completedQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        Long locationId,
        String serialNos,
        String remark
) {
    public ProductionCompletionRequest(LocalDate completionDate, BigDecimal completedQty, String remark) {
        this(completionDate, completedQty, null, null, null, null, null, remark);
    }

    public ProductionCompletionRequest(
            LocalDate completionDate,
            BigDecimal completedQty,
            String lotNo,
            LocalDate productionDate,
            LocalDate expiryDate,
            String remark
    ) {
        this(completionDate, completedQty, lotNo, productionDate, expiryDate, null, null, remark);
    }

    public ProductionCompletionRequest(LocalDate completionDate, String remark) {
        this(completionDate, null, null, null, null, null, null, remark);
    }
}
