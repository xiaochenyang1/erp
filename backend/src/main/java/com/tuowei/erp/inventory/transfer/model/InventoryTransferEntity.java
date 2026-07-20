package com.tuowei.erp.inventory.transfer.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("inv_transfer")
public class InventoryTransferEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long companyId;

    private Long accountBookId;

    private String transferNo;

    private Long fromWarehouseId;

    private Long toWarehouseId;

    private LocalDate transferDate;

    private String status;

    private BigDecimal totalQuantity;

    private BigDecimal totalAmount;

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

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }

    public Long getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(Long fromWarehouseId) { this.fromWarehouseId = fromWarehouseId; }

    public Long getToWarehouseId() { return toWarehouseId; }
    public void setToWarehouseId(Long toWarehouseId) { this.toWarehouseId = toWarehouseId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

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
