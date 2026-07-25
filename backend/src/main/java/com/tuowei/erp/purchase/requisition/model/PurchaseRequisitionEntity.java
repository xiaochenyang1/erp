package com.tuowei.erp.purchase.requisition.model;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate; import java.time.LocalDateTime;
@TableName("pur_requisition")
public class PurchaseRequisitionEntity {
    @TableId(type=IdType.ASSIGN_ID) private Long id; private Long companyId; private Long accountBookId;
    private String requisitionNo; private LocalDate requisitionDate; private Long requestDeptId; private Long requestUserId;
    private LocalDate neededDate; private String status; private Long supplierId; private Long convertedOrderId;
    private String convertedOrderNo; private LocalDateTime convertedTime; private String remark; private Integer deletedFlag;
    private Long createdBy; private LocalDateTime createdTime; private Long updatedBy; private LocalDateTime updatedTime; @Version private Integer version;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCompanyId(){return companyId;} public void setCompanyId(Long companyId){this.companyId=companyId;}
    public Long getAccountBookId(){return accountBookId;} public void setAccountBookId(Long accountBookId){this.accountBookId=accountBookId;}
    public String getRequisitionNo(){return requisitionNo;} public void setRequisitionNo(String requisitionNo){this.requisitionNo=requisitionNo;}
    public LocalDate getRequisitionDate(){return requisitionDate;} public void setRequisitionDate(LocalDate requisitionDate){this.requisitionDate=requisitionDate;}
    public Long getRequestDeptId(){return requestDeptId;} public void setRequestDeptId(Long requestDeptId){this.requestDeptId=requestDeptId;}
    public Long getRequestUserId(){return requestUserId;} public void setRequestUserId(Long requestUserId){this.requestUserId=requestUserId;}
    public LocalDate getNeededDate(){return neededDate;} public void setNeededDate(LocalDate neededDate){this.neededDate=neededDate;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public Long getSupplierId(){return supplierId;} public void setSupplierId(Long supplierId){this.supplierId=supplierId;}
    public Long getConvertedOrderId(){return convertedOrderId;} public void setConvertedOrderId(Long convertedOrderId){this.convertedOrderId=convertedOrderId;}
    public String getConvertedOrderNo(){return convertedOrderNo;} public void setConvertedOrderNo(String convertedOrderNo){this.convertedOrderNo=convertedOrderNo;}
    public LocalDateTime getConvertedTime(){return convertedTime;} public void setConvertedTime(LocalDateTime convertedTime){this.convertedTime=convertedTime;}
    public String getRemark(){return remark;} public void setRemark(String remark){this.remark=remark;}
    public Integer getDeletedFlag(){return deletedFlag;} public void setDeletedFlag(Integer deletedFlag){this.deletedFlag=deletedFlag;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long createdBy){this.createdBy=createdBy;}
    public LocalDateTime getCreatedTime(){return createdTime;} public void setCreatedTime(LocalDateTime createdTime){this.createdTime=createdTime;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long updatedBy){this.updatedBy=updatedBy;}
    public LocalDateTime getUpdatedTime(){return updatedTime;} public void setUpdatedTime(LocalDateTime updatedTime){this.updatedTime=updatedTime;}
    public Integer getVersion(){return version;} public void setVersion(Integer version){this.version=version;}
}
