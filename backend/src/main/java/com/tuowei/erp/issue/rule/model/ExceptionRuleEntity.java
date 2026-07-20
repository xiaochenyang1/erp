package com.tuowei.erp.issue.rule.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("biz_exception_rule")
public class ExceptionRuleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private String category;
    private String priority;
    private BigDecimal thresholdValue;
    private String thresholdUnit;
    private Integer enabled;
    private Long assigneeUserId;
    private Integer scheduleIntervalMinutes;
    private LocalDateTime nextScanTime;
    private String remark;
    private LocalDateTime lastScanTime;
    private String lastScanStatus;
    private Integer lastHitCount;
    private Integer lastTicketCreatedCount;
    private String lastErrorMessage;
    private Integer deletedFlag;
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
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getThresholdUnit() { return thresholdUnit; }
    public void setThresholdUnit(String thresholdUnit) { this.thresholdUnit = thresholdUnit; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public Integer getScheduleIntervalMinutes() { return scheduleIntervalMinutes; }
    public void setScheduleIntervalMinutes(Integer scheduleIntervalMinutes) { this.scheduleIntervalMinutes = scheduleIntervalMinutes; }
    public LocalDateTime getNextScanTime() { return nextScanTime; }
    public void setNextScanTime(LocalDateTime nextScanTime) { this.nextScanTime = nextScanTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getLastScanTime() { return lastScanTime; }
    public void setLastScanTime(LocalDateTime lastScanTime) { this.lastScanTime = lastScanTime; }
    public String getLastScanStatus() { return lastScanStatus; }
    public void setLastScanStatus(String lastScanStatus) { this.lastScanStatus = lastScanStatus; }
    public Integer getLastHitCount() { return lastHitCount; }
    public void setLastHitCount(Integer lastHitCount) { this.lastHitCount = lastHitCount; }
    public Integer getLastTicketCreatedCount() { return lastTicketCreatedCount; }
    public void setLastTicketCreatedCount(Integer lastTicketCreatedCount) { this.lastTicketCreatedCount = lastTicketCreatedCount; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Integer getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(Integer deletedFlag) { this.deletedFlag = deletedFlag; }
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
