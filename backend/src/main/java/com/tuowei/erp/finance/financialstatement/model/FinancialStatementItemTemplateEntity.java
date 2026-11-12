package com.tuowei.erp.finance.financialstatement.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_financial_statement_item_template")
public class FinancialStatementItemTemplateEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String formulaVersion;
    private String statementType;
    private String itemCode;
    private String itemName;
    private String sectionCode;
    private String amountDirection;
    private String formulaCode;
    private Integer sortNo;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }
    public String getStatementType() { return statementType; }
    public void setStatementType(String statementType) { this.statementType = statementType; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
    public String getAmountDirection() { return amountDirection; }
    public void setAmountDirection(String amountDirection) { this.amountDirection = amountDirection; }
    public String getFormulaCode() { return formulaCode; }
    public void setFormulaCode(String formulaCode) { this.formulaCode = formulaCode; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
