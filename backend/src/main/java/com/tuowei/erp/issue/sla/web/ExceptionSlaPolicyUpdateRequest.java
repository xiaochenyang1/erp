package com.tuowei.erp.issue.sla.web;

public class ExceptionSlaPolicyUpdateRequest {

    private Integer dueHours;
    private Boolean escalationEnabled;
    private String escalateToPriority;
    private Boolean enabled;
    private String remark;

    public Integer getDueHours() { return dueHours; }
    public void setDueHours(Integer dueHours) { this.dueHours = dueHours; }
    public Boolean getEscalationEnabled() { return escalationEnabled; }
    public void setEscalationEnabled(Boolean escalationEnabled) { this.escalationEnabled = escalationEnabled; }
    public String getEscalateToPriority() { return escalateToPriority; }
    public void setEscalateToPriority(String escalateToPriority) { this.escalateToPriority = escalateToPriority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
