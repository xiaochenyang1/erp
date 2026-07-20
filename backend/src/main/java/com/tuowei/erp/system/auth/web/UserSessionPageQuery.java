package com.tuowei.erp.system.auth.web;

import java.time.LocalDateTime;

public class UserSessionPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private Long userId;

    private String username;

    private String status;

    private LocalDateTime issuedAtFrom;

    private LocalDateTime issuedAtTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAtFrom() {
        return issuedAtFrom;
    }

    public void setIssuedAtFrom(LocalDateTime issuedAtFrom) {
        this.issuedAtFrom = issuedAtFrom;
    }

    public LocalDateTime getIssuedAtTo() {
        return issuedAtTo;
    }

    public void setIssuedAtTo(LocalDateTime issuedAtTo) {
        this.issuedAtTo = issuedAtTo;
    }
}
