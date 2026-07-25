package com.tuowei.erp.purchase.inquiry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("pur_inquiry")
public class PurchaseInquiryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String inquiryNo;
    private LocalDate inquiryDate;
    private String status;
    private Long selectedSupplierId;
    private Long selectedQuoteId;
    private Long convertedOrderId;
    private String convertedOrderNo;
    private Long convertedBy;
    private LocalDateTime convertedTime;
    private String title;
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
    public String getInquiryNo() { return inquiryNo; }
    public void setInquiryNo(String inquiryNo) { this.inquiryNo = inquiryNo; }
    public LocalDate getInquiryDate() { return inquiryDate; }
    public void setInquiryDate(LocalDate inquiryDate) { this.inquiryDate = inquiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSelectedSupplierId() { return selectedSupplierId; }
    public void setSelectedSupplierId(Long selectedSupplierId) { this.selectedSupplierId = selectedSupplierId; }
    public Long getSelectedQuoteId() { return selectedQuoteId; }
    public void setSelectedQuoteId(Long selectedQuoteId) { this.selectedQuoteId = selectedQuoteId; }
    public Long getConvertedOrderId() { return convertedOrderId; }
    public void setConvertedOrderId(Long convertedOrderId) { this.convertedOrderId = convertedOrderId; }
    public String getConvertedOrderNo() { return convertedOrderNo; }
    public void setConvertedOrderNo(String convertedOrderNo) { this.convertedOrderNo = convertedOrderNo; }
    public Long getConvertedBy() { return convertedBy; }
    public void setConvertedBy(Long convertedBy) { this.convertedBy = convertedBy; }
    public LocalDateTime getConvertedTime() { return convertedTime; }
    public void setConvertedTime(LocalDateTime convertedTime) { this.convertedTime = convertedTime; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
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
