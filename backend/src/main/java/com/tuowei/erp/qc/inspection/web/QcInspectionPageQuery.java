package com.tuowei.erp.qc.inspection.web;

import java.time.LocalDate;

public class QcInspectionPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private Long receiptId;

    private Long deliveryId;

    private String inspectionType;

    private String status;

    private LocalDate inspectionDateFrom;

    private LocalDate inspectionDateTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getInspectionType() {
        return inspectionType;
    }

    public void setInspectionType(String inspectionType) {
        this.inspectionType = inspectionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getInspectionDateFrom() {
        return inspectionDateFrom;
    }

    public void setInspectionDateFrom(LocalDate inspectionDateFrom) {
        this.inspectionDateFrom = inspectionDateFrom;
    }

    public LocalDate getInspectionDateTo() {
        return inspectionDateTo;
    }

    public void setInspectionDateTo(LocalDate inspectionDateTo) {
        this.inspectionDateTo = inspectionDateTo;
    }
}
