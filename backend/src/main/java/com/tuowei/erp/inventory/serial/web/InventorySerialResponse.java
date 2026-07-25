package com.tuowei.erp.inventory.serial.web;

import java.time.LocalDateTime;

public record InventorySerialResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        Long warehouseId,
        Long locationId,
        String serialNo,
        String status,
        String inboundBizType,
        String inboundBizNo,
        String outboundBizType,
        String outboundBizNo,
        String remark,
        LocalDateTime updatedTime
) {}
