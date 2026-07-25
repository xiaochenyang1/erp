package com.tuowei.erp.inventory.mrp.web;

public class MrpRunPageQuery {
    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private String status;

    public Long getPageNo() { return pageNo; }
    public void setPageNo(Long pageNo) { this.pageNo = pageNo; }
    public Long getPageSize() { return pageSize; }
    public void setPageSize(Long pageSize) { this.pageSize = pageSize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
