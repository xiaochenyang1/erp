package com.tuowei.erp.sales.delivery.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesDeliveryCreateRequest(
        @NotNull(message = "orderId不能为空") Long orderId,
        @NotNull(message = "warehouseId不能为空") Long warehouseId,
        @NotNull(message = "deliveryDate不能为空") LocalDate deliveryDate,
        String remark,
        String carrierName,
        String trackingNo,
        String logisticsStatus,
        String deliveredBy,
        Long deliveryProofAttachmentId,
        @Valid @NotEmpty(message = "lines不能为空") List<SalesDeliveryLineRequest> lines
) {
    public SalesDeliveryCreateRequest(Long orderId, Long warehouseId, LocalDate deliveryDate, String remark,
                                      String carrierName, String trackingNo, String logisticsStatus,
                                      List<SalesDeliveryLineRequest> lines) {
        this(orderId, warehouseId, deliveryDate, remark, carrierName, trackingNo, logisticsStatus, null, null, lines);
    }
}
