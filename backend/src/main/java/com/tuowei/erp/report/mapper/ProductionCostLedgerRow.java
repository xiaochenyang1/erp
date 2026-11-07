package com.tuowei.erp.report.mapper;

import java.math.BigDecimal;

public class ProductionCostLedgerRow {
    private Long orderId;
    private BigDecimal ledgerAmount;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getLedgerAmount() { return ledgerAmount; }
    public void setLedgerAmount(BigDecimal ledgerAmount) { this.ledgerAmount = ledgerAmount; }
}
