package com.tuowei.erp.issue.web;

public class ExceptionTicketPageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private String priority;
    private String category;
    private Long assigneeUserId;
    private String sourceNo;
    private Boolean overdueOnly;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getSourceNo() { return sourceNo; }
    public void setSourceNo(String sourceNo) { this.sourceNo = sourceNo; }
    public Boolean getOverdueOnly() { return overdueOnly; }
    public void setOverdueOnly(Boolean overdueOnly) { this.overdueOnly = overdueOnly; }
}
