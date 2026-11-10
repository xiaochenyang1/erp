package com.tuowei.erp.inventory.stock.web;

public record InventoryLotGenealogyResponse(
        LotGenealogyNode root,
        LotGenealogyNode upstream,
        LotGenealogyNode downstream,
        GenealogyLimits limits
) {
}
