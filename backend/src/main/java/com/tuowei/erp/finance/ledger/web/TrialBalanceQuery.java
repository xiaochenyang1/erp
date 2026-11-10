package com.tuowei.erp.finance.ledger.web;

import java.time.LocalDate;

public class TrialBalanceQuery {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
}
