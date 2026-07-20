package com.tuowei.erp.inventory.stock.web;

import java.time.LocalDate;

public class InventoryLotBalancePageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 20;

    private Long warehouseId;

    private Long productId;

    private String lotNo;

    private LocalDate expiryDateFrom;

    private LocalDate expiryDateTo;

    private Integer expiringWithinDays;

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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public LocalDate getExpiryDateFrom() {
        return expiryDateFrom;
    }

    public void setExpiryDateFrom(LocalDate expiryDateFrom) {
        this.expiryDateFrom = expiryDateFrom;
    }

    public LocalDate getExpiryDateTo() {
        return expiryDateTo;
    }

    public void setExpiryDateTo(LocalDate expiryDateTo) {
        this.expiryDateTo = expiryDateTo;
    }

    public Integer getExpiringWithinDays() {
        return expiringWithinDays;
    }

    public void setExpiringWithinDays(Integer expiringWithinDays) {
        this.expiringWithinDays = expiringWithinDays;
    }
}
