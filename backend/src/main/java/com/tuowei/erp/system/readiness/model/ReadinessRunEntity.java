package com.tuowei.erp.system.readiness.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("sys_readiness_run")
public class ReadinessRunEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String runNo;
    private String releaseCommit;
    private String releaseVersion;
    private String environment;
    private String databaseInstance;
    private String redisInstance;
    private String dockerProfile;
    private String status;
    private String decision;
    private String decisionComment;
    private String remark;
    private Long startedBy;
    private LocalDateTime startedTime;
    private Long decidedBy;
    private LocalDateTime decidedTime;
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
    public String getReleaseCommit() { return releaseCommit; }
    public void setReleaseCommit(String releaseCommit) { this.releaseCommit = releaseCommit; }
    public String getReleaseVersion() { return releaseVersion; }
    public void setReleaseVersion(String releaseVersion) { this.releaseVersion = releaseVersion; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getDatabaseInstance() { return databaseInstance; }
    public void setDatabaseInstance(String databaseInstance) { this.databaseInstance = databaseInstance; }
    public String getRedisInstance() { return redisInstance; }
    public void setRedisInstance(String redisInstance) { this.redisInstance = redisInstance; }
    public String getDockerProfile() { return dockerProfile; }
    public void setDockerProfile(String dockerProfile) { this.dockerProfile = dockerProfile; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getStartedBy() { return startedBy; }
    public void setStartedBy(Long startedBy) { this.startedBy = startedBy; }
    public LocalDateTime getStartedTime() { return startedTime; }
    public void setStartedTime(LocalDateTime startedTime) { this.startedTime = startedTime; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedTime() { return decidedTime; }
    public void setDecidedTime(LocalDateTime decidedTime) { this.decidedTime = decidedTime; }
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
