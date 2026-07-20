package com.tuowei.erp.inventory.transfer.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryTransferResponse(
        Long id,
        String transferNo,
        Long fromWarehouseId,
        Long toWarehouseId,
        LocalDate transferDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        String remark,
        List<InventoryTransferLineResponse> lines
) {}