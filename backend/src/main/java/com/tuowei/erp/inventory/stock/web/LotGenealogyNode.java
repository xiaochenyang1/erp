package com.tuowei.erp.inventory.stock.web;

import java.time.LocalDate;
import java.util.List;

public record LotGenealogyNode(
        Long productId,
        String productCode,
        String productName,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        int depth,
        List<LotGenealogyLink> links
) {
}
