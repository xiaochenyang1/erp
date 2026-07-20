package com.tuowei.erp.workflow.web;

public class WorkflowRecordPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private String action;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
