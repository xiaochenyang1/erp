package com.tuowei.erp.report.web;

import java.time.LocalDateTime;

public class InventoryTransactionReportQuery {

    private Integer pageNo;

    private Integer pageSize;

    private Long warehouseId;

    private Long productId;

    private String bizType;

    private String bizNo;

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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
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
