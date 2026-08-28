package com.tuowei.erp.masterdata.customerproduct.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

@TableName("md_customer_product_relation")
public class CustomerProductRelationEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long companyId; private Long accountBookId; private Long customerId; private Long productId;
    private String customerProductCode; private String customerProductName; private String deliveryPreference; private String packagingPreference; private String remark;
    private String status; private Integer deletedFlag; private Long createdBy; private LocalDateTime createdTime; private Long updatedBy; private LocalDateTime updatedTime;
    @Version private Integer version;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getCompanyId(){return companyId;} public void setCompanyId(Long v){companyId=v;} public Long getAccountBookId(){return accountBookId;} public void setAccountBookId(Long v){accountBookId=v;} public Long getCustomerId(){return customerId;} public void setCustomerId(Long v){customerId=v;} public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;} public String getCustomerProductCode(){return customerProductCode;} public void setCustomerProductCode(String v){customerProductCode=v;} public String getCustomerProductName(){return customerProductName;} public void setCustomerProductName(String v){customerProductName=v;} public String getDeliveryPreference(){return deliveryPreference;} public void setDeliveryPreference(String v){deliveryPreference=v;} public String getPackagingPreference(){return packagingPreference;} public void setPackagingPreference(String v){packagingPreference=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public Integer getDeletedFlag(){return deletedFlag;} public void setDeletedFlag(Integer v){deletedFlag=v;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;} public LocalDateTime getCreatedTime(){return createdTime;} public void setCreatedTime(LocalDateTime v){createdTime=v;} public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;} public LocalDateTime getUpdatedTime(){return updatedTime;} public void setUpdatedTime(LocalDateTime v){updatedTime=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
