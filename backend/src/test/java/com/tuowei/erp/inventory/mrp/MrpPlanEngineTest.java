package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot.*;
import com.tuowei.erp.inventory.mrp.service.MrpPlanEngine;
import com.tuowei.erp.inventory.mrp.service.MrpPlanEngine.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class MrpPlanEngineTest {
    private final Map<Long, Product> products = new HashMap<>(Map.of(
            1L, new Product(1L, "A", "成品", "AUTO", null),
            2L, new Product(2L, "B", "半成品", "AUTO", null),
            3L, new Product(3L, "C", "原料", "AUTO", null),
            4L, new Product(4L, "D", "另一成品", "AUTO", null)));
    private final Map<Long, List<Bom>> boms = new HashMap<>();
    private final Map<Key, List<Source>> demands = new HashMap<>();
    private final Map<Key, List<Supply>> supplies = new HashMap<>();
    private final List<Warning> warnings = new ArrayList<>();

    @Test void expandsThreeLevelsAfterNettingEachWarehouseProductOnce() {
        chain(); demand(1, 10, "10"); stock(2, 10, "4"); stock(3, 10, "10");
        var result = calculate();
        assertNode(result, 1, 10, "10", "10", 0, "PRODUCTION");
        assertNode(result, 2, 10, "20", "16", 1, "PRODUCTION");
        assertNode(result, 3, 10, "48", "38", 2, "PURCHASE");
        assertThat(node(result, 3, 10).sources()).singleElement().satisfies(source -> {
            assertThat(source.parentProductId()).isEqualTo(2L);
            assertThat(source.parentNetQty()).isEqualByComparingTo("16");
            assertThat(source.qtyPer()).isEqualByComparingTo("3");
        });
        assertThat(node(result, 1, 10).sources().get(0).documentNo()).isEqualTo("SO-1");
    }

    @Test void aggregatesDirectDemandAndEveryParentBeforeConsumingSharedStock() {
        chain(); bom(4, material(2, "1"));
        demand(1, 10, "10"); demand(4, 10, "5"); demand(2, 10, "3"); stock(2, 10, "4");
        var result = calculate();
        assertNode(result, 2, 10, "28", "24", 1, "PRODUCTION");
        assertNode(result, 3, 10, "72", "72", 2, "PURCHASE");
        assertThat(node(result, 2, 10).sources()).hasSize(3);
    }

    @Test void doesNotMoveInventoryOrSupplyBetweenWarehouses() {
        demand(3, 10, "10"); demand(3, 20, "8"); stock(3, 10, "4"); stock(3, 20, "100");
        supply(3, 10, "PURCHASE_ORDER", "2"); supply(3, 20, "PRODUCTION_ORDER", "20");
        var result = calculate();
        assertNode(result, 3, 10, "10", "4", 0, "PURCHASE");
        assertNode(result, 3, 20, "8", "0", 0, "PURCHASE");
    }

    @Test void expandsMaterialsInConfiguredMaterialWarehouse() {
        chain(); products.put(1L, new Product(1L, "A", "成品", "PRODUCTION", 20L));
        demand(1, 10, "10"); stock(2, 20, "4"); stock(3, 20, "10"); stock(2, 10, "100");
        var result = calculate();
        assertNode(result, 2, 20, "20", "16", 1, "PRODUCTION");
        assertNode(result, 3, 20, "48", "38", 2, "PURCHASE");
        assertThat(node(result, 1, 10).materialWarehouseId()).isEqualTo(20L);
    }

    @Test void explicitPurchaseStopsExpansionEvenWithAnActiveBom() {
        chain(); products.put(2L, new Product(2L, "B", "半成品", "PURCHASE", null));
        demand(1, 10, "10"); stock(2, 10, "4");
        var result = calculate();
        assertNode(result, 2, 10, "20", "16", 1, "PURCHASE");
        assertThat(result.nodes()).hasSize(2);
    }

    @Test void refusesExplicitProductionWithoutBomAndAmbiguousActiveBoms() {
        products.put(1L, new Product(1L, "A", "成品", "PRODUCTION", null)); demand(1, 10, "10");
        assertThatThrownBy(this::calculate).hasMessageContaining("没有启用BOM");
        bom(1, material(2, "2"));
        boms.put(1L, List.of(boms.get(1L).get(0), new Bom(999L, "duplicate", 1L, 0, List.of(material(3, "1")))));
        assertThatThrownBy(this::calculate).hasMessageContaining("多个启用BOM");
    }

    @Test void reportsCycleWithProductAndWarehousePath() {
        chain(); bom(3, material(1, "1")); demand(1, 10, "10");
        assertThatThrownBy(this::calculate).hasMessageContaining("循环BOM", "A@主仓", "B@主仓", "C@主仓");
    }

    @Test void ignoresUnreachableCycles() {
        bom(1, material(2, "1")); bom(2, material(1, "1")); demand(3, 10, "10");
        assertThat(calculate().nodes()).hasSize(1);
    }

    @Test void roundsBaseQuantityAndLossLikeProductionOrderMaterialSnapshot() {
        bom(1, new Material(3L, bd("3.3333"), bd("2"), bd("0.05"))); demand(1, 10, "7");
        assertNode(calculate(), 3, 10, "12.2499", "12.2499", 1, "PURCHASE");
    }

    @Test void existingProductionSupplyAndUnissuedMaterialsAreSeparateFacts() {
        chain(); demand(1, 10, "10"); supply(1, 10, "PRODUCTION_ORDER", "6");
        demands.put(new Key(2L, 10L), List.of(Source.direct("PRODUCTION_MATERIAL", 800L, "MO-800", 801L, bd("8"))));
        stock(2, 10, "4"); supply(2, 10, "PLANNED_PRODUCTION", "3"); stock(3, 10, "10");
        var result = calculate();
        assertNode(result, 1, 10, "10", "4", 0, "PRODUCTION");
        assertNode(result, 2, 10, "16", "9", 1, "PRODUCTION");
        assertNode(result, 3, 10, "27", "17", 2, "PURCHASE");
        assertThat(node(result, 2, 10).plannedSupplyQty()).isEqualByComparingTo("3");
    }

    @Test void retainsFullyCoveredRequirementsForTraceabilityWithoutExpandingGrossQuantity() {
        chain(); demand(1, 10, "10"); stock(1, 10, "10");
        var result = calculate();
        assertNode(result, 1, 10, "10", "0", 0, "PRODUCTION");
        assertNode(result, 2, 10, "0", "0", 1, "PRODUCTION");
        assertThat(node(result, 1, 10).sources()).hasSize(1);
    }

    @Test void showsUnallocatedPurchaseWarningsOnlyForProductsInThisPlan() {
        chain(); demand(1, 10, "10");
        warnings.add(new Warning("PURCHASE_WAREHOUSE_REQUIRED", 90L, "PO-90", 2L, bd("3"), "补仓"));
        warnings.add(new Warning("PURCHASE_WAREHOUSE_REQUIRED", 91L, "PO-91", 4L, bd("5"), "补仓"));
        assertThat(calculate().warnings()).singleElement().extracting(Warning::documentId).isEqualTo(90L);
    }

    @Test void rejectsInactiveReferencesAndInvalidBomQuantities() {
        demand(1, 99, "10");
        assertThatThrownBy(this::calculate).hasMessageContaining("仓库不存在或未启用");
        demands.clear(); demand(1, 10, "10"); bom(1, new Material(2L, bd("1"), bd("0"), bd("0")));
        assertThatThrownBy(this::calculate).hasMessageContaining("基准数量");
        bom(1, material(99, "1"));
        assertThatThrownBy(this::calculate).hasMessageContaining("商品不存在或未启用");
    }

    private void chain() { bom(1, material(2, "2")); bom(2, material(3, "3")); }
    private void bom(long product, Material... materials) {
        boms.put(product, List.of(new Bom(product + 100, "BOM-" + product, product, 7, List.of(materials))));
    }
    private Material material(long product, String qty) { return new Material(product, bd(qty), bd("1"), bd("0")); }
    private void demand(long product, long warehouse, String qty) {
        demands.computeIfAbsent(new Key(product, warehouse), ignored -> new ArrayList<>())
                .add(Source.direct("SALES_ORDER", product + 200, "SO-" + product, product + 300, bd(qty)));
    }
    private void stock(long product, long warehouse, String qty) { supply(product, warehouse, "ON_HAND", qty); }
    private void supply(long product, long warehouse, String type, String qty) {
        supplies.computeIfAbsent(new Key(product, warehouse), ignored -> new ArrayList<>())
                .add(new Supply(type, product + 400, type + product, product + 500, bd(qty)));
    }
    private MrpCalculationSnapshot calculate() {
        return new MrpPlanEngine().calculate(new Input(products, Map.of(10L, "主仓", 20L, "材料仓"), boms, demands, supplies, warnings));
    }
    private Node node(MrpCalculationSnapshot result, long product, long warehouse) {
        return result.nodes().stream().filter(n -> n.productId() == product && n.warehouseId() == warehouse).findFirst().orElseThrow();
    }
    private void assertNode(MrpCalculationSnapshot result, long product, long warehouse, String demand, String net, int level, String type) {
        Node node = node(result, product, warehouse);
        assertThat(node.demandQty()).isEqualByComparingTo(demand);
        assertThat(node.netQty()).isEqualByComparingTo(net);
        assertThat(node.level()).isEqualTo(level);
        assertThat(node.supplyType()).isEqualTo(type);
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
