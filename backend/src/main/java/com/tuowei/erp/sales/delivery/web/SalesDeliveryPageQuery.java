package com.tuowei.erp.sales.delivery.web;

import java.time.LocalDate;

public class SalesDeliveryPageQuery {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private Long orderId;

    private Long warehouseId;

    private String status;
    private String trackingNo;
    private String logisticsStatus;

    private LocalDate deliveryDateFrom;

    private LocalDate deliveryDateTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getTrackingNo(){return trackingNo;} public void setTrackingNo(String trackingNo){this.trackingNo=trackingNo;} public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDeliveryDateFrom() {
        return deliveryDateFrom;
    }

    public void setDeliveryDateFrom(LocalDate deliveryDateFrom) {
        this.deliveryDateFrom = deliveryDateFrom;
    }

    public LocalDate getDeliveryDateTo() {
        return deliveryDateTo;
    }

    public void setDeliveryDateTo(LocalDate deliveryDateTo) {
        this.deliveryDateTo = deliveryDateTo;
    }

    public String getLogisticsStatus(){return logisticsStatus;}
    public void setLogisticsStatus(String logisticsStatus){this.logisticsStatus=logisticsStatus;}
}
