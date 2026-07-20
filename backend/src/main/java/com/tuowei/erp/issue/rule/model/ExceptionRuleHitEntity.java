package com.tuowei.erp.issue.rule.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("biz_exception_rule_hit")
public class ExceptionRuleHitEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long ruleId;
    private String ruleCode;
    private String ruleType;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String sourceRoute;
    private String hitKey;
    private String title;
    private String description;
    private String triggerValue;
    private String thresholdValue;
    private Long ticketId;
    private Integer hitCount;
    private LocalDateTime firstHitTime;
    private LocalDateTime lastHitTime;
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
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceNo() { return sourceNo; }
    public void setSourceNo(String sourceNo) { this.sourceNo = sourceNo; }
    public String getSourceRoute() { return sourceRoute; }
    public void setSourceRoute(String sourceRoute) { this.sourceRoute = sourceRoute; }
    public String getHitKey() { return hitKey; }
    public void setHitKey(String hitKey) { this.hitKey = hitKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTriggerValue() { return triggerValue; }
    public void setTriggerValue(String triggerValue) { this.triggerValue = triggerValue; }
    public String getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(String thresholdValue) { this.thresholdValue = thresholdValue; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public LocalDateTime getFirstHitTime() { return firstHitTime; }
    public void setFirstHitTime(LocalDateTime firstHitTime) { this.firstHitTime = firstHitTime; }
    public LocalDateTime getLastHitTime() { return lastHitTime; }
    public void setLastHitTime(LocalDateTime lastHitTime) { this.lastHitTime = lastHitTime; }
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
