package com.tuowei.erp.inventory.replenishment.web;

import java.time.LocalDateTime;

public class InventoryReplenishmentSuggestionPageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String suggestionNo;
    private String status;
    private Long warehouseId;
    private Long productId;
    private Long supplierId;
    private LocalDateTime createdTimeFrom;
    private LocalDateTime createdTimeTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getSuggestionNo() { return suggestionNo; }
    public void setSuggestionNo(String suggestionNo) { this.suggestionNo = suggestionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDateTime getCreatedTimeFrom() { return createdTimeFrom; }
    public void setCreatedTimeFrom(LocalDateTime createdTimeFrom) { this.createdTimeFrom = createdTimeFrom; }
    public LocalDateTime getCreatedTimeTo() { return createdTimeTo; }
    public void setCreatedTimeTo(LocalDateTime createdTimeTo) { this.createdTimeTo = createdTimeTo; }
}
