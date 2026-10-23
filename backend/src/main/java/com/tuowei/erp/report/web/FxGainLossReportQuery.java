package com.tuowei.erp.report.web;

import java.time.LocalDate;

/** 汇兑损益报表过滤条件：按结算日期区间、方向、币种和往来单位筛选。 */
public class FxGainLossReportQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String direction;
    private String currencyCode;
    private Long partnerId;
    private LocalDate settlementDateFrom;
    private LocalDate settlementDateTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public LocalDate getSettlementDateFrom() {
        return settlementDateFrom;
    }

    public void setSettlementDateFrom(LocalDate settlementDateFrom) {
        this.settlementDateFrom = settlementDateFrom;
    }

    public LocalDate getSettlementDateTo() {
        return settlementDateTo;
    }

    public void setSettlementDateTo(LocalDate settlementDateTo) {
        this.settlementDateTo = settlementDateTo;
    }
}
