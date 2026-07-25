package com.tuowei.erp.sales.returnorder.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("sal_return_line")
public class SalesReturnLineEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long companyId;

    private Long accountBookId;

    private Long returnId;

    private Integer lineNo;

    private Long deliveryLineId;

    private Long orderLineId;

    private Long productId;

    private BigDecimal qty;

    private BigDecimal price;

    private BigDecimal taxRate;

    private BigDecimal amount;

    private BigDecimal taxAmount;

    private String lotNo;

    private Long locationId;

    private String serialNos;

    private LocalDate productionDate;

    private LocalDate expiryDate;

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
    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getDeliveryLineId() { return deliveryLineId; }
    public void setDeliveryLineId(Long deliveryLineId) { this.deliveryLineId = deliveryLineId; }
    public Long getOrderLineId() { return orderLineId; }
    public void setOrderLineId(Long orderLineId) { this.orderLineId = orderLineId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getSerialNos() { return serialNos; }
    public void setSerialNos(String serialNos) { this.serialNos = serialNos; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
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
