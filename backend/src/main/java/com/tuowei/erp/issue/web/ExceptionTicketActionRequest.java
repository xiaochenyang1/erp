package com.tuowei.erp.issue.web;

public class ExceptionTicketActionRequest {

    private String comment;

    public ExceptionTicketActionRequest() {
    }

    public ExceptionTicketActionRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
