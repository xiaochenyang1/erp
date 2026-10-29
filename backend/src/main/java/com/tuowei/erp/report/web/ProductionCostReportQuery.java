package com.tuowei.erp.report.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ProductionCostReportQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private Long productId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedStartDateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedStartDateTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public LocalDate getPlannedStartDateFrom() { return plannedStartDateFrom; }
    public void setPlannedStartDateFrom(LocalDate plannedStartDateFrom) { this.plannedStartDateFrom = plannedStartDateFrom; }
    public LocalDate getPlannedStartDateTo() { return plannedStartDateTo; }
    public void setPlannedStartDateTo(LocalDate plannedStartDateTo) { this.plannedStartDateTo = plannedStartDateTo; }
}
