package com.tuowei.erp.finance.fx.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fin_fx_revaluation")
public class FxRevaluationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long periodId;
    private String status;
    private BigDecimal openOriginalTotal;
    private BigDecimal arAdjustment;
    private BigDecimal apAdjustment;
    private LocalDate revaluationDate;
    private LocalDate reversalDate;
    private Long generation;
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
    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getOpenOriginalTotal() { return openOriginalTotal; }
    public void setOpenOriginalTotal(BigDecimal openOriginalTotal) { this.openOriginalTotal = openOriginalTotal; }
    public BigDecimal getArAdjustment() { return arAdjustment; }
    public void setArAdjustment(BigDecimal arAdjustment) { this.arAdjustment = arAdjustment; }
    public BigDecimal getApAdjustment() { return apAdjustment; }
    public void setApAdjustment(BigDecimal apAdjustment) { this.apAdjustment = apAdjustment; }
    public LocalDate getRevaluationDate() { return revaluationDate; }
    public void setRevaluationDate(LocalDate revaluationDate) { this.revaluationDate = revaluationDate; }
    public LocalDate getReversalDate() { return reversalDate; }
    public void setReversalDate(LocalDate reversalDate) { this.reversalDate = reversalDate; }
    public Long getGeneration() { return generation; }
    public void setGeneration(Long generation) { this.generation = generation; }
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
