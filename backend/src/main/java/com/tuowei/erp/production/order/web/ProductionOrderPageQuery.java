package com.tuowei.erp.production.order.web;

public class ProductionOrderPageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private Long bomId;
    private Long productId;
    private Long materialWarehouseId;
    private Long finishedWarehouseId;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getMaterialWarehouseId() { return materialWarehouseId; }
    public void setMaterialWarehouseId(Long materialWarehouseId) { this.materialWarehouseId = materialWarehouseId; }
    public Long getFinishedWarehouseId() { return finishedWarehouseId; }
    public void setFinishedWarehouseId(Long finishedWarehouseId) { this.finishedWarehouseId = finishedWarehouseId; }
}
