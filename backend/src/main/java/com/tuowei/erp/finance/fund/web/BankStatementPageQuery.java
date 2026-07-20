package com.tuowei.erp.finance.fund.web;

import java.time.LocalDate;

public class BankStatementPageQuery {
    private Long fundAccountId;
    private String direction;
    private String status;
    private LocalDate transactionDateFrom;
    private LocalDate transactionDateTo;
    private String matchedBizType;
    private String matchedBizNo;
    private Integer pageNo;
    private Integer pageSize;

    public Long getFundAccountId() { return fundAccountId; }
    public void setFundAccountId(Long fundAccountId) { this.fundAccountId = fundAccountId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getTransactionDateFrom() { return transactionDateFrom; }
    public void setTransactionDateFrom(LocalDate transactionDateFrom) { this.transactionDateFrom = transactionDateFrom; }
    public LocalDate getTransactionDateTo() { return transactionDateTo; }
    public void setTransactionDateTo(LocalDate transactionDateTo) { this.transactionDateTo = transactionDateTo; }
    public String getMatchedBizType() { return matchedBizType; }
    public void setMatchedBizType(String matchedBizType) { this.matchedBizType = matchedBizType; }
    public String getMatchedBizNo() { return matchedBizNo; }
    public void setMatchedBizNo(String matchedBizNo) { this.matchedBizNo = matchedBizNo; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
