package com.tuowei.erp.finance.receivable.web;

import java.time.LocalDate;

public class ReceivablePageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String receivableNo;
    private Long customerId;
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

    public String getReceivableNo() {
        return receivableNo;
    }

    public void setReceivableNo(String receivableNo) {
        this.receivableNo = receivableNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
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
