package com.tuowei.erp.finance.period.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("fin_period_close_snapshot_item")
public class AccountPeriodCloseSnapshotItemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long snapshotId;
    private String checkCode;
    private String checkTitle;
    private String checkCategory;
    private Integer passedFlag;
    private String checkMessage;
    private BigDecimal metric;
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
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String checkCode) { this.checkCode = checkCode; }
    public String getCheckTitle() { return checkTitle; }
    public void setCheckTitle(String checkTitle) { this.checkTitle = checkTitle; }
    public String getCheckCategory() { return checkCategory; }
    public void setCheckCategory(String checkCategory) { this.checkCategory = checkCategory; }
    public Integer getPassedFlag() { return passedFlag; }
    public void setPassedFlag(Integer passedFlag) { this.passedFlag = passedFlag; }
    public String getCheckMessage() { return checkMessage; }
    public void setCheckMessage(String checkMessage) { this.checkMessage = checkMessage; }
    public BigDecimal getMetric() { return metric; }
    public void setMetric(BigDecimal metric) { this.metric = metric; }
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
