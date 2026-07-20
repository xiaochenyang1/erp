package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public class AuditLogPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String auditType;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private String action;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime auditTimeFrom;
    private LocalDateTime auditTimeTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getAuditType() { return auditType; }
    public void setAuditType(String auditType) { this.auditType = auditType; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getAuditTimeFrom() { return auditTimeFrom; }
    public void setAuditTimeFrom(LocalDateTime auditTimeFrom) { this.auditTimeFrom = auditTimeFrom; }
    public LocalDateTime getAuditTimeTo() { return auditTimeTo; }
    public void setAuditTimeTo(LocalDateTime auditTimeTo) { this.auditTimeTo = auditTimeTo; }
}
