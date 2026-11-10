package com.tuowei.erp.finance.financialstatement.web;

import java.time.LocalDate;

public class BalanceSheetQuery {

    private LocalDate asOfDate;

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
}
