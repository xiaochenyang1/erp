package com.tuowei.erp.inventory.stock.web;

import java.time.LocalDateTime;

public class InventoryReservationPageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private Long warehouseId;
    private Long productId;
    private String sourceType;
    private String sourceNo;
    private String status;
    private LocalDateTime createdTimeFrom;
    private LocalDateTime createdTimeTo;

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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedTimeFrom() {
        return createdTimeFrom;
    }

    public void setCreatedTimeFrom(LocalDateTime createdTimeFrom) {
        this.createdTimeFrom = createdTimeFrom;
    }

    public LocalDateTime getCreatedTimeTo() {
        return createdTimeTo;
    }

    public void setCreatedTimeTo(LocalDateTime createdTimeTo) {
        this.createdTimeTo = createdTimeTo;
    }
}
