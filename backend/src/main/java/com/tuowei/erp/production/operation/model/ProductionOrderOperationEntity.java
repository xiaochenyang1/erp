package com.tuowei.erp.production.operation.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("prd_order_operation")
public class ProductionOrderOperationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long orderId;
    private Long routingId;
    private Long routingOperationId;
    private Integer lineNo;
    private String operationCode;
    private String operationName;
    private Long workCenterId;
    private BigDecimal plannedQty;
    private BigDecimal reportedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal scrapQty;
    private String status;
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
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public Long getRoutingOperationId() { return routingOperationId; }
    public void setRoutingOperationId(Long routingOperationId) { this.routingOperationId = routingOperationId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public Long getWorkCenterId() { return workCenterId; }
    public void setWorkCenterId(Long workCenterId) { this.workCenterId = workCenterId; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public BigDecimal getReportedQty() { return reportedQty; }
    public void setReportedQty(BigDecimal reportedQty) { this.reportedQty = reportedQty; }
    public BigDecimal getQualifiedQty() { return qualifiedQty; }
    public void setQualifiedQty(BigDecimal qualifiedQty) { this.qualifiedQty = qualifiedQty; }
    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal scrapQty) { this.scrapQty = scrapQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
