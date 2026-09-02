package com.tuowei.erp.finance.budget.web;

import java.math.BigDecimal;

public class BudgetExecutionQuery {
    private Integer budgetYear;
    private Integer periodMonth;
    private Long deptId;
    private Long subjectId;
    private BigDecimal amount;
    public Integer getBudgetYear() { return budgetYear; }
    public void setBudgetYear(Integer budgetYear) { this.budgetYear = budgetYear; }
    public Integer getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(Integer periodMonth) { this.periodMonth = periodMonth; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
