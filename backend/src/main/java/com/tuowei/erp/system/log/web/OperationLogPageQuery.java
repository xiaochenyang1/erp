package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public class OperationLogPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String bizNo;
    private String result;
    private LocalDateTime operationTimeFrom;
    private LocalDateTime operationTimeTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getOperationTimeFrom() { return operationTimeFrom; }
    public void setOperationTimeFrom(LocalDateTime operationTimeFrom) { this.operationTimeFrom = operationTimeFrom; }
    public LocalDateTime getOperationTimeTo() { return operationTimeTo; }
    public void setOperationTimeTo(LocalDateTime operationTimeTo) { this.operationTimeTo = operationTimeTo; }
}
