package com.tuowei.erp.inventory.mrp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("inv_mrp_run")
public class MrpRunEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String runNo;
    private LocalDate asOfDate;
    private String status;
    private Integer purchaseCount;
    private Integer productionCount;
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
    public String getRunNo() { return runNo; }
    public void setRunNo(String runNo) { this.runNo = runNo; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPurchaseCount() { return purchaseCount; }
    public void setPurchaseCount(Integer purchaseCount) { this.purchaseCount = purchaseCount; }
    public Integer getProductionCount() { return productionCount; }
    public void setProductionCount(Integer productionCount) { this.productionCount = productionCount; }
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
