package com.tuowei.erp.finance.ledger.web;

import java.time.LocalDate;

public class LedgerQuery {

    private String subjectCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
}
