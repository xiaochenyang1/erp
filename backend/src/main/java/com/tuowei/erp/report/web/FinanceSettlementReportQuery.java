package com.tuowei.erp.report.web;

import java.time.LocalDate;

public class FinanceSettlementReportQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String direction;
    private Long partnerId;
    private String status;
    private String sourceType;
    private LocalDate bizDateFrom;
    private LocalDate bizDateTo;

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

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDate getBizDateFrom() {
        return bizDateFrom;
    }

    public void setBizDateFrom(LocalDate bizDateFrom) {
        this.bizDateFrom = bizDateFrom;
    }

    public LocalDate getBizDateTo() {
        return bizDateTo;
    }

    public void setBizDateTo(LocalDate bizDateTo) {
        this.bizDateTo = bizDateTo;
    }
}
