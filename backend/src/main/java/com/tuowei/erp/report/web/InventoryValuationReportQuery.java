package com.tuowei.erp.report.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class InventoryValuationReportQuery {
    private Integer pageNo;
    private Integer pageSize;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate asOfDate;
    private Long warehouseId;
    private Long productId;
    private String keyword;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
