package com.tuowei.erp.inventory.replenishment.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryReplenishmentSuggestionResponse(
        Long id,
        String suggestionNo,
        String sourceType,
        Long sourceRuleId,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        Long supplierId,
        String supplierName,
        BigDecimal suggestedQty,
        BigDecimal shortageQtySnapshot,
        LocalDate expectedArrivalDate,
        String status,
        String fulfillmentStatus,
        Long purchaseOrderId,
        String purchaseOrderNo,
        String remark,
        LocalDateTime createdTime
) {
}
