package com.tuowei.erp.system.readiness.web;

import java.time.LocalDateTime;

public class ReadinessRunPageQuery {

    private String releaseCommit;
    private String environment;
    private String status;
    private String decision;
    private LocalDateTime createdTimeFrom;
    private LocalDateTime createdTimeTo;
    private Integer pageNo;
    private Integer pageSize;

    public String getReleaseCommit() { return releaseCommit; }
    public void setReleaseCommit(String releaseCommit) { this.releaseCommit = releaseCommit; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public LocalDateTime getCreatedTimeFrom() { return createdTimeFrom; }
    public void setCreatedTimeFrom(LocalDateTime createdTimeFrom) { this.createdTimeFrom = createdTimeFrom; }
    public LocalDateTime getCreatedTimeTo() { return createdTimeTo; }
    public void setCreatedTimeTo(LocalDateTime createdTimeTo) { this.createdTimeTo = createdTimeTo; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
