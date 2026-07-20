package com.tuowei.erp.inventory.stock.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("inv_reservation_event")
public class InventoryReservationEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long companyId;

    private Long accountBookId;

    private Long reservationId;

    private Long warehouseId;

    private Long productId;

    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long sourceLineId;

    private String eventType;

    private BigDecimal eventQty;

    private BigDecimal remainingQtyBefore;

    private BigDecimal remainingQtyAfter;

    private String reason;

    private Long createdBy;

    private LocalDateTime createdTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getAccountBookId() {
        return accountBookId;
    }

    public void setAccountBookId(Long accountBookId) {
        this.accountBookId = accountBookId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(Long sourceLineId) {
        this.sourceLineId = sourceLineId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public BigDecimal getEventQty() {
        return eventQty;
    }

    public void setEventQty(BigDecimal eventQty) {
        this.eventQty = eventQty;
    }

    public BigDecimal getRemainingQtyBefore() {
        return remainingQtyBefore;
    }

    public void setRemainingQtyBefore(BigDecimal remainingQtyBefore) {
        this.remainingQtyBefore = remainingQtyBefore;
    }

    public BigDecimal getRemainingQtyAfter() {
        return remainingQtyAfter;
    }

    public void setRemainingQtyAfter(BigDecimal remainingQtyAfter) {
        this.remainingQtyAfter = remainingQtyAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
