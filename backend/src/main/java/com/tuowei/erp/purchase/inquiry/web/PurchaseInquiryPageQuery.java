package com.tuowei.erp.purchase.inquiry.web;

import java.time.LocalDate;

public class PurchaseInquiryPageQuery {

    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String status;
    private LocalDate inquiryDateFrom;
    private LocalDate inquiryDateTo;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getInquiryDateFrom() { return inquiryDateFrom; }
    public void setInquiryDateFrom(LocalDate inquiryDateFrom) { this.inquiryDateFrom = inquiryDateFrom; }
    public LocalDate getInquiryDateTo() { return inquiryDateTo; }
    public void setInquiryDateTo(LocalDate inquiryDateTo) { this.inquiryDateTo = inquiryDateTo; }
}
