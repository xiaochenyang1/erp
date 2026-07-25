package com.tuowei.erp.finance.payable.web;

import java.time.LocalDate;

public class PayablePageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String payableNo;
    private Long supplierId;
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

    public String getPayableNo() {
        return payableNo;
    }

    public void setPayableNo(String payableNo) {
        this.payableNo = payableNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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
