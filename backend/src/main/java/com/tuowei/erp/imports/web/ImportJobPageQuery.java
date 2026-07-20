package com.tuowei.erp.imports.web;

import java.time.LocalDateTime;

public class ImportJobPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private String importType;

    private String status;

    private Long createdBy;

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

    public String getImportType() {
        return importType;
    }

    public void setImportType(String importType) {
        this.importType = importType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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
