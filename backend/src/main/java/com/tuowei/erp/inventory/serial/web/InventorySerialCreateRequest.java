package com.tuowei.erp.inventory.serial.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventorySerialCreateRequest(
        @NotNull Long productId,
        Long warehouseId,
        Long locationId,
        @NotBlank String serialNo,
        String inboundBizType,
        String inboundBizNo,
        String remark
) {}
