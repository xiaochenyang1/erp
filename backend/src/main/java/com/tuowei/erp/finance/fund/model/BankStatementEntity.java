package com.tuowei.erp.finance.fund.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fin_bank_statement")
public class BankStatementEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long fundAccountId;
    private String statementNo;
    private String externalTxnNo;
    private LocalDate transactionDate;
    private String direction;
    private BigDecimal amount;
    private String counterpartyName;
    private String summary;
    private String status;
    private String matchedBizType;
    private Long matchedBizId;
    private String matchedBizNo;
    private LocalDateTime matchedTime;
    private Long matchedBy;
    private String unmatchReason;
    private Integer deletedFlag;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getAccountBookId() { return accountBookId; }
    public void setAccountBookId(Long accountBookId) { this.accountBookId = accountBookId; }
    public Long getFundAccountId() { return fundAccountId; }
    public void setFundAccountId(Long fundAccountId) { this.fundAccountId = fundAccountId; }
    public String getStatementNo() { return statementNo; }
    public void setStatementNo(String statementNo) { this.statementNo = statementNo; }
    public String getExternalTxnNo() { return externalTxnNo; }
    public void setExternalTxnNo(String externalTxnNo) { this.externalTxnNo = externalTxnNo; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMatchedBizType() { return matchedBizType; }
    public void setMatchedBizType(String matchedBizType) { this.matchedBizType = matchedBizType; }
    public Long getMatchedBizId() { return matchedBizId; }
    public void setMatchedBizId(Long matchedBizId) { this.matchedBizId = matchedBizId; }
    public String getMatchedBizNo() { return matchedBizNo; }
    public void setMatchedBizNo(String matchedBizNo) { this.matchedBizNo = matchedBizNo; }
    public LocalDateTime getMatchedTime() { return matchedTime; }
    public void setMatchedTime(LocalDateTime matchedTime) { this.matchedTime = matchedTime; }
    public Long getMatchedBy() { return matchedBy; }
    public void setMatchedBy(Long matchedBy) { this.matchedBy = matchedBy; }
    public String getUnmatchReason() { return unmatchReason; }
    public void setUnmatchReason(String unmatchReason) { this.unmatchReason = unmatchReason; }
    public Integer getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(Integer deletedFlag) { this.deletedFlag = deletedFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
