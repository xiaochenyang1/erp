package com.tuowei.erp.inventory.replenishment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("inv_replenishment_suggestion")
public class InventoryReplenishmentSuggestionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String suggestionNo;
    private String sourceType;
    private Long sourceRuleId;
    private Long warehouseId;
    private Long productId;
    private Long supplierId;
    private BigDecimal suggestedQty;
    private BigDecimal shortageQtySnapshot;
    private LocalDate expectedArrivalDate;
    private String status;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private String remark;
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
    public String getSuggestionNo() { return suggestionNo; }
    public void setSuggestionNo(String suggestionNo) { this.suggestionNo = suggestionNo; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceRuleId() { return sourceRuleId; }
    public void setSourceRuleId(Long sourceRuleId) { this.sourceRuleId = sourceRuleId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getSuggestedQty() { return suggestedQty; }
    public void setSuggestedQty(BigDecimal suggestedQty) { this.suggestedQty = suggestedQty; }
    public BigDecimal getShortageQtySnapshot() { return shortageQtySnapshot; }
    public void setShortageQtySnapshot(BigDecimal shortageQtySnapshot) { this.shortageQtySnapshot = shortageQtySnapshot; }
    public LocalDate getExpectedArrivalDate() { return expectedArrivalDate; }
    public void setExpectedArrivalDate(LocalDate expectedArrivalDate) { this.expectedArrivalDate = expectedArrivalDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public String getPurchaseOrderNo() { return purchaseOrderNo; }
    public void setPurchaseOrderNo(String purchaseOrderNo) { this.purchaseOrderNo = purchaseOrderNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
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
