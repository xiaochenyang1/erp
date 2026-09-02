package com.tuowei.erp.finance.budget.web;

public class BudgetPageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private Integer budgetYear;
    private String status;
    private String keyword;
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Integer getBudgetYear() { return budgetYear; }
    public void setBudgetYear(Integer budgetYear) { this.budgetYear = budgetYear; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
