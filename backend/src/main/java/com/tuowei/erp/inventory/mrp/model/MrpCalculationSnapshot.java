package com.tuowei.erp.inventory.mrp.model;

import java.math.BigDecimal;
import java.util.List;

/** Immutable explanation of the inputs and each warehouse/product calculation, including zero shortages. */
public record MrpCalculationSnapshot(int algorithmVersion, List<Node> nodes, List<Warning> warnings) {
    public static final int CURRENT_VERSION = 2;

    public record Node(
            Long productId, String productCode, String productName,
            Long warehouseId, String warehouseName, String supplyType,
            Long materialWarehouseId, String materialWarehouseName,
            Long bomId, String bomNo, Integer bomVersion, int level,
            BigDecimal demandQty, BigDecimal onHandQty, BigDecimal purchaseSupplyQty,
            BigDecimal productionSupplyQty, BigDecimal plannedSupplyQty, BigDecimal netQty,
            List<Source> sources, List<Supply> supplies, List<Material> materials
    ) {
        public BigDecimal openSupplyQty() {
            return purchaseSupplyQty.add(productionSupplyQty).add(plannedSupplyQty);
        }
    }

    /** BOM sources point to a parent node; following these links recovers every root order without path duplication. */
    public record Source(
            String type, Long documentId, String documentNo, Long lineId, BigDecimal qty,
            Long parentProductId, Long parentWarehouseId, Long bomId,
            BigDecimal parentNetQty, BigDecimal qtyPer, BigDecimal baseQty, BigDecimal lossRate
    ) {
        public static Source direct(String type, Long documentId, String documentNo, Long lineId, BigDecimal qty) {
            return new Source(type, documentId, documentNo, lineId, qty, null, null, null, null, null, null, null);
        }
    }

    public record Supply(String type, Long documentId, String documentNo, Long lineId, BigDecimal qty) { }
    public record Material(Long productId, BigDecimal qtyPer, BigDecimal baseQty, BigDecimal lossRate) { }
    public record Warning(String code, Long documentId, String documentNo, Long productId, BigDecimal qty, String message) { }
}
