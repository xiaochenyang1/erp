package com.tuowei.erp.issue.rule.web;

import java.math.BigDecimal;

public class ExceptionRuleUpdateRequest {

    private BigDecimal thresholdValue;
    private String thresholdUnit;
    private String priority;
    private Long assigneeUserId;
    private Integer scheduleIntervalMinutes;
    private String remark;

    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getThresholdUnit() { return thresholdUnit; }
    public void setThresholdUnit(String thresholdUnit) { this.thresholdUnit = thresholdUnit; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public Integer getScheduleIntervalMinutes() { return scheduleIntervalMinutes; }
    public void setScheduleIntervalMinutes(Integer scheduleIntervalMinutes) { this.scheduleIntervalMinutes = scheduleIntervalMinutes; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
