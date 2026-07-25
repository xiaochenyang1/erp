package com.tuowei.erp.inventory.serial.web;

public class InventorySerialPageQuery {
    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private Long productId;
    private Long warehouseId;
    private String status;
    private String keyword;
    public Long getPageNo() { return pageNo; }
    public void setPageNo(Long pageNo) { this.pageNo = pageNo; }
    public Long getPageSize() { return pageSize; }
    public void setPageSize(Long pageSize) { this.pageSize = pageSize; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
