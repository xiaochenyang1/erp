package com.tuowei.erp.issue.web;

import java.time.LocalDateTime;

public class ExceptionTicketCreateRequest {

    private String category;
    private String priority;
    private String title;
    private String description;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String sourceRoute;
    private Long assigneeUserId;
    private LocalDateTime dueTime;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceNo() { return sourceNo; }
    public void setSourceNo(String sourceNo) { this.sourceNo = sourceNo; }
    public String getSourceRoute() { return sourceRoute; }
    public void setSourceRoute(String sourceRoute) { this.sourceRoute = sourceRoute; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
}
