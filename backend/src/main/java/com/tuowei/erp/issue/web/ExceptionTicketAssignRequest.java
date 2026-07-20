package com.tuowei.erp.issue.web;

public class ExceptionTicketAssignRequest {

    private Long assigneeUserId;
    private String comment;

    public ExceptionTicketAssignRequest() {
    }

    public ExceptionTicketAssignRequest(Long assigneeUserId, String comment) {
        this.assigneeUserId = assigneeUserId;
        this.comment = comment;
    }

    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
