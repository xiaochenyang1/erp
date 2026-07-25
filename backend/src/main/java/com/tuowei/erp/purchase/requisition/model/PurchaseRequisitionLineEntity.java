package com.tuowei.erp.purchase.requisition.model;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal; import java.time.LocalDateTime;
@TableName("pur_requisition_line")
public class PurchaseRequisitionLineEntity {
    @TableId(type=IdType.ASSIGN_ID) private Long id; private Long companyId; private Long accountBookId; private Long requisitionId;
    private Integer lineNo; private Long productId; private BigDecimal qty; private String remark; private Integer deletedFlag;
    private Long createdBy; private LocalDateTime createdTime; private Long updatedBy; private LocalDateTime updatedTime; @Version private Integer version;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getCompanyId(){return companyId;} public void setCompanyId(Long companyId){this.companyId=companyId;}
    public Long getAccountBookId(){return accountBookId;} public void setAccountBookId(Long accountBookId){this.accountBookId=accountBookId;}
    public Long getRequisitionId(){return requisitionId;} public void setRequisitionId(Long requisitionId){this.requisitionId=requisitionId;}
    public Integer getLineNo(){return lineNo;} public void setLineNo(Integer lineNo){this.lineNo=lineNo;}
    public Long getProductId(){return productId;} public void setProductId(Long productId){this.productId=productId;}
    public BigDecimal getQty(){return qty;} public void setQty(BigDecimal qty){this.qty=qty;}
    public String getRemark(){return remark;} public void setRemark(String remark){this.remark=remark;}
    public Integer getDeletedFlag(){return deletedFlag;} public void setDeletedFlag(Integer deletedFlag){this.deletedFlag=deletedFlag;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long createdBy){this.createdBy=createdBy;}
    public LocalDateTime getCreatedTime(){return createdTime;} public void setCreatedTime(LocalDateTime createdTime){this.createdTime=createdTime;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long updatedBy){this.updatedBy=updatedBy;}
    public LocalDateTime getUpdatedTime(){return updatedTime;} public void setUpdatedTime(LocalDateTime updatedTime){this.updatedTime=updatedTime;}
    public Integer getVersion(){return version;} public void setVersion(Integer version){this.version=version;}
}
