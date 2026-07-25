package com.tuowei.erp.purchase.requisition.web;
import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List;
public record PurchaseRequisitionResponse(
    Long id, String requisitionNo, LocalDate requisitionDate, LocalDate neededDate, String status,
    Long supplierId, Long convertedOrderId, String convertedOrderNo, LocalDateTime convertedTime,
    String remark, List<PurchaseRequisitionLineResponse> lines
) {}
