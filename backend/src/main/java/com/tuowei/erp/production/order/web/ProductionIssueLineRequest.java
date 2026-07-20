package com.tuowei.erp.production.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionIssueLineRequest(
        Long orderMaterialId,
        BigDecimal issueQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public ProductionIssueLineRequest(Long orderMaterialId, BigDecimal issueQty, String remark) {
        this(orderMaterialId, issueQty, null, null, null, remark);
    }
}
