package com.tuowei.erp.production.order.web;

import java.time.LocalDate;

public class ProductionOrderPageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private Long bomId;
    private Long productId;
    private Long materialWarehouseId;
    private Long finishedWarehouseId;
    private LocalDate plannedStartDateFrom;
    private LocalDate plannedStartDateTo;

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
    public LocalDate getPlannedStartDateFrom() { return plannedStartDateFrom; }
    public void setPlannedStartDateFrom(LocalDate plannedStartDateFrom) { this.plannedStartDateFrom = plannedStartDateFrom; }
    public LocalDate getPlannedStartDateTo() { return plannedStartDateTo; }
    public void setPlannedStartDateTo(LocalDate plannedStartDateTo) { this.plannedStartDateTo = plannedStartDateTo; }
}
