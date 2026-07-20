package com.tuowei.erp.finance.subject.web;

public class AccountSubjectPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String subjectCode;
    private String subjectName;
    private String subjectType;
    private String status;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
