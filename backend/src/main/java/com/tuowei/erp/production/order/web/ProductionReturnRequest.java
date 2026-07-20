package com.tuowei.erp.production.order.web;

import java.time.LocalDate;
import java.util.List;

public record ProductionReturnRequest(LocalDate returnDate, String remark, List<ProductionReturnLineRequest> lines) {
}
