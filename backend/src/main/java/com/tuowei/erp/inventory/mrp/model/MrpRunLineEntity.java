package com.tuowei.erp.inventory.mrp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("inv_mrp_run_line")
public class MrpRunLineEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long runId;
    private Integer lineNo;
    private Long productId;
    private String suggestionType;
    private BigDecimal demandQty;
    private BigDecimal onHandQty;
    private BigDecimal openSupplyQty;
    private BigDecimal netQty;
    private Long bomId;
    private String reason;
    private String status;
    private String convertedBizType;
    private Long convertedBizId;
    private String convertedBizNo;
    private LocalDateTime convertedTime;
    private Integer deletedFlag;
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
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSuggestionType() { return suggestionType; }
    public void setSuggestionType(String suggestionType) { this.suggestionType = suggestionType; }
    public BigDecimal getDemandQty() { return demandQty; }
    public void setDemandQty(BigDecimal demandQty) { this.demandQty = demandQty; }
    public BigDecimal getOnHandQty() { return onHandQty; }
    public void setOnHandQty(BigDecimal onHandQty) { this.onHandQty = onHandQty; }
    public BigDecimal getOpenSupplyQty() { return openSupplyQty; }
    public void setOpenSupplyQty(BigDecimal openSupplyQty) { this.openSupplyQty = openSupplyQty; }
    public BigDecimal getNetQty() { return netQty; }
    public void setNetQty(BigDecimal netQty) { this.netQty = netQty; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConvertedBizType() { return convertedBizType; }
    public void setConvertedBizType(String convertedBizType) { this.convertedBizType = convertedBizType; }
    public Long getConvertedBizId() { return convertedBizId; }
    public void setConvertedBizId(Long convertedBizId) { this.convertedBizId = convertedBizId; }
    public String getConvertedBizNo() { return convertedBizNo; }
    public void setConvertedBizNo(String convertedBizNo) { this.convertedBizNo = convertedBizNo; }
    public LocalDateTime getConvertedTime() { return convertedTime; }
    public void setConvertedTime(LocalDateTime convertedTime) { this.convertedTime = convertedTime; }
    public Integer getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(Integer deletedFlag) { this.deletedFlag = deletedFlag; }
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
