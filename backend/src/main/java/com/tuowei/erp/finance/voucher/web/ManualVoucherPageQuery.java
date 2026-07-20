package com.tuowei.erp.finance.voucher.web;

import java.time.LocalDate;

public class ManualVoucherPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String voucherNo;
    private String status;
    private LocalDate dateFrom;
    private LocalDate dateTo;

    public Integer getPageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize == null || pageSize < 1 ? 20 : pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }
}
