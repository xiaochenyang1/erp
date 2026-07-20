package com.tuowei.erp.inventory.stock.web;

import java.time.LocalDateTime;

public class InventoryLotTraceQuery {

    private Integer pageNo;

    private Integer pageSize;

    private Long productId;

    private String lotNo;

    private Long warehouseId;

    private String direction;

    private LocalDateTime occurredTimeFrom;

    private LocalDateTime occurredTimeTo;

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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LocalDateTime getOccurredTimeFrom() {
        return occurredTimeFrom;
    }

    public void setOccurredTimeFrom(LocalDateTime occurredTimeFrom) {
        this.occurredTimeFrom = occurredTimeFrom;
    }

    public LocalDateTime getOccurredTimeTo() {
        return occurredTimeTo;
    }

    public void setOccurredTimeTo(LocalDateTime occurredTimeTo) {
        this.occurredTimeTo = occurredTimeTo;
    }
}
