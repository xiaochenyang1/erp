package com.tuowei.erp.production.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("prd_order")
public class ProductionOrderEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String orderNo;
    private Long bomId;
    private Long productId;
    private Long materialWarehouseId;
    private Long finishedWarehouseId;
    private BigDecimal plannedQty;
    private BigDecimal completedQty;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private String status;
    private BigDecimal issuedAmount;
    private BigDecimal finishedAmount;
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
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getMaterialWarehouseId() { return materialWarehouseId; }
    public void setMaterialWarehouseId(Long materialWarehouseId) { this.materialWarehouseId = materialWarehouseId; }
    public Long getFinishedWarehouseId() { return finishedWarehouseId; }
    public void setFinishedWarehouseId(Long finishedWarehouseId) { this.finishedWarehouseId = finishedWarehouseId; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public BigDecimal getCompletedQty() { return completedQty; }
    public void setCompletedQty(BigDecimal completedQty) { this.completedQty = completedQty; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public LocalDate getPlannedFinishDate() { return plannedFinishDate; }
    public void setPlannedFinishDate(LocalDate plannedFinishDate) { this.plannedFinishDate = plannedFinishDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getIssuedAmount() { return issuedAmount; }
    public void setIssuedAmount(BigDecimal issuedAmount) { this.issuedAmount = issuedAmount; }
    public BigDecimal getFinishedAmount() { return finishedAmount; }
    public void setFinishedAmount(BigDecimal finishedAmount) { this.finishedAmount = finishedAmount; }
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
