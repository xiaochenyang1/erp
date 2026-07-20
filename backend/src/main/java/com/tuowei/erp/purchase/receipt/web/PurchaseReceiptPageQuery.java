package com.tuowei.erp.purchase.receipt.web;

import java.time.LocalDate;

public class PurchaseReceiptPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private Long orderId;

    private Long warehouseId;

    private String status;

    private LocalDate receiptDateFrom;

    private LocalDate receiptDateTo;

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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public LocalDate getReceiptDateFrom() {
        return receiptDateFrom;
    }

    public void setReceiptDateFrom(LocalDate receiptDateFrom) {
        this.receiptDateFrom = receiptDateFrom;
    }

    public LocalDate getReceiptDateTo() {
        return receiptDateTo;
    }

    public void setReceiptDateTo(LocalDate receiptDateTo) {
        this.receiptDateTo = receiptDateTo;
    }
}
