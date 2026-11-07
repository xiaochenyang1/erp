package com.tuowei.erp.sales.delivery.web;

import jakarta.validation.constraints.NotBlank;

public record SalesDeliveryLogisticsUpdateRequest(
        @NotBlank(message = "logisticsStatus不能为空") String logisticsStatus,
        String carrierName,
        String trackingNo,
        String deliveredBy,
        Long deliveryProofAttachmentId,
        String remark
) {
    public SalesDeliveryLogisticsUpdateRequest(String logisticsStatus, String carrierName, String trackingNo, String remark) {
        this(logisticsStatus, carrierName, trackingNo, null, null, remark);
    }
}
