package com.tuowei.erp.purchase.returnorder.web;

import java.time.LocalDate;

public class PurchaseReturnPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private Long receiptId;

    private Long warehouseId;

    private String status;

    private LocalDate returnDateFrom;

    private LocalDate returnDateTo;

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

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getReturnDateFrom() {
        return returnDateFrom;
    }

    public void setReturnDateFrom(LocalDate returnDateFrom) {
        this.returnDateFrom = returnDateFrom;
    }

    public LocalDate getReturnDateTo() {
        return returnDateTo;
    }

    public void setReturnDateTo(LocalDate returnDateTo) {
        this.returnDateTo = returnDateTo;
    }
}
