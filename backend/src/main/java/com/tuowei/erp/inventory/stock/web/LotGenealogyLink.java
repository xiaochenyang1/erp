package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LotGenealogyLink(
        String bizType,
        String bizNo,
        String bizLabel,
        String documentRoute,
        LocalDateTime occurredTime,
        BigDecimal qty,
        Long warehouseId,
        String warehouseName,
        CounterpartyRef counterparty,
        String terminalReason,
        LotGenealogyNode node
) {
}
