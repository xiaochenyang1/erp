package com.tuowei.erp.workflow.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("wf_approval_task")
public class WorkflowTaskEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long instanceId;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private String title;
    private Long approvalNodeId;
    private Long approverUserId;
    private String status;
    private LocalDateTime dueTime;
    private LocalDateTime escalatedTime;
    private Integer escalationCount;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getAccountBookId() { return accountBookId; }
    public void setAccountBookId(Long accountBookId) { this.accountBookId = accountBookId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getApprovalNodeId() { return approvalNodeId; }
    public void setApprovalNodeId(Long approvalNodeId) { this.approvalNodeId = approvalNodeId; }
    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
    public LocalDateTime getEscalatedTime() { return escalatedTime; }
    public void setEscalatedTime(LocalDateTime escalatedTime) { this.escalatedTime = escalatedTime; }
    public Integer getEscalationCount() { return escalationCount; }
    public void setEscalationCount(Integer escalationCount) { this.escalationCount = escalationCount; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
