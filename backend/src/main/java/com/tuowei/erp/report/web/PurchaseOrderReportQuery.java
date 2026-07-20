package com.tuowei.erp.report.web;

import java.time.LocalDate;

public class PurchaseOrderReportQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private Long supplierId;
    private LocalDate orderDateFrom;
    private LocalDate orderDateTo;
    private String status;
    private String approvalStatus;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public LocalDate getOrderDateFrom() {
        return orderDateFrom;
    }

    public void setOrderDateFrom(LocalDate orderDateFrom) {
        this.orderDateFrom = orderDateFrom;
    }

    public LocalDate getOrderDateTo() {
        return orderDateTo;
    }

    public void setOrderDateTo(LocalDate orderDateTo) {
        this.orderDateTo = orderDateTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
}
