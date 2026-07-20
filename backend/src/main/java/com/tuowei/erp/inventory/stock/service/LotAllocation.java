package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;

import java.math.BigDecimal;

public record LotAllocation(
        InventoryLotBalanceEntity lot,
        BigDecimal qty,
        BigDecimal amount
) {
}
