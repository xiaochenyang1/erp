package com.tuowei.erp.production.order.web;

import java.time.LocalDate;
import java.util.List;

public record ProductionIssueRequest(LocalDate issueDate, String remark, List<ProductionIssueLineRequest> lines) {
    public ProductionIssueRequest(LocalDate issueDate, String remark) {
        this(issueDate, remark, null);
    }
}
