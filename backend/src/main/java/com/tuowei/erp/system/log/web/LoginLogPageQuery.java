package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public class LoginLogPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private Long userId;
    private String username;
    private String result;
    private LocalDateTime loginTimeFrom;
    private LocalDateTime loginTimeTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getLoginTimeFrom() { return loginTimeFrom; }
    public void setLoginTimeFrom(LocalDateTime loginTimeFrom) { this.loginTimeFrom = loginTimeFrom; }
    public LocalDateTime getLoginTimeTo() { return loginTimeTo; }
    public void setLoginTimeTo(LocalDateTime loginTimeTo) { this.loginTimeTo = loginTimeTo; }
}
