package com.tuowei.erp.finance.period.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fin_period_financial_report_snapshot")
public class PeriodFinancialReportSnapshotEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private Long periodId;
    private Long closeSnapshotId;
    private String reportType;
    private String formulaVersion;
    private String configurationDigest;
    private String configurationPayload;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer verifiedFlag;
    private String reportPayload;
    private String payloadSha256;
    private Long capturedBy;
    private LocalDateTime capturedTime;
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
    public Long getCloseSnapshotId() { return closeSnapshotId; }
    public void setCloseSnapshotId(Long closeSnapshotId) { this.closeSnapshotId = closeSnapshotId; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }
    public String getConfigurationDigest() { return configurationDigest; }
    public void setConfigurationDigest(String configurationDigest) { this.configurationDigest = configurationDigest; }
    public String getConfigurationPayload() { return configurationPayload; }
    public void setConfigurationPayload(String configurationPayload) { this.configurationPayload = configurationPayload; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public Integer getVerifiedFlag() { return verifiedFlag; }
    public void setVerifiedFlag(Integer verifiedFlag) { this.verifiedFlag = verifiedFlag; }
    public String getReportPayload() { return reportPayload; }
    public void setReportPayload(String reportPayload) { this.reportPayload = reportPayload; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
    public Long getCapturedBy() { return capturedBy; }
    public void setCapturedBy(Long capturedBy) { this.capturedBy = capturedBy; }
    public LocalDateTime getCapturedTime() { return capturedTime; }
    public void setCapturedTime(LocalDateTime capturedTime) { this.capturedTime = capturedTime; }
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
