package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MrpPlanCalculationServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(9001L, 101L, 202L, LocalDateTime.of(2026, 10, 15, 11, 30));
    private String failingQuery;
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Map<Long, BigDecimal> sales = new HashMap<>(), safety = new HashMap<>(), stock = new HashMap<>(),
            purchase = new HashMap<>(), production = new HashMap<>();
    private final Map<Long, Long> boms = new HashMap<>();
    private final List<Map<String, Object>> components = new ArrayList<>();

    @Test
    void combinesSalesAndSafetyTargetBeforeNettingSupplyAndExpandingBom() {
        sales.put(1L, bd("10")); safety.put(1L, bd("12")); stock.put(1L, bd("3"));
        purchase.put(1L, bd("2")); production.put(1L, bd("1"));
        stock.put(2L, bd("1")); purchase.put(2L, bd("1")); bom(1, 2, "2");
        var result = calculate();
        assertThat(result.productionLines()).singleElement().satisfies(line -> {
            assertThat(line.demandQty()).isEqualByComparingTo("22");
            assertThat(line.netQty()).isEqualByComparingTo("16");
            assertThat(line.reason()).contains("销售未发货", "安全库存");
        });
        assertThat(result.purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(2L);
            assertThat(line.demandQty()).isEqualByComparingTo("32");
            assertThat(line.netQty()).isEqualByComparingTo("30");
        });
    }

    @ParameterizedTest
    @CsvSource({"4,6", "6,4", "10,0", "12,0"})
    void safetyStockUsesInventoryExactlyOnce(String onHand, String expected) {
        safety.put(1L, bd("10")); stock.put(1L, bd(onHand));
        var result = calculate();
        assertThat(result.productionLines()).isEmpty();
        assertThat(total(result.purchaseLines(), 1)).isEqualByComparingTo(expected);
    }

    @Test
    void replenishesSafetyConsumedBySalesEvenWhenCurrentStockExceedsTarget() {
        sales.put(1L, bd("5")); safety.put(1L, bd("10")); stock.put(1L, bd("12"));
        assertThat(total(calculate().purchaseLines(), 1)).isEqualByComparingTo("3");
    }

    @Test
    void doesNotSuggestWhenSupplyCoversDemand() {
        sales.put(1L, bd("5")); stock.put(1L, bd("2"));
        purchase.put(1L, bd("2")); production.put(1L, bd("1"));
        var result = calculate();
        assertThat(result.productionLines()).isEmpty();
        assertThat(result.purchaseLines()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"5,6", "20,0"})
    void combinesDirectSalesAndBomDemandBeforeUsingSharedStock(String onHand, String expected) {
        sales.put(1L, bd("6")); sales.put(2L, bd("5")); stock.put(2L, bd(onHand)); bom(1, 2, "1");
        var result = calculate();
        assertThat(total(result.productionLines(), 1)).isEqualByComparingTo("6");
        assertThat(total(result.purchaseLines(), 2)).isEqualByComparingTo(expected);
        assertThat(result.purchaseLines().stream().filter(l -> l.productId() == 2L).count()).isLessThanOrEqualTo(1);
    }

    @Test
    void combinesSeveralBomsSalesAndSafetyBeforeNettingSharedIncomingSupply() {
        sales.put(1L, bd("6")); sales.put(3L, bd("3")); sales.put(2L, bd("5"));
        safety.put(2L, bd("2")); stock.put(2L, bd("5")); purchase.put(2L, bd("3")); production.put(2L, bd("1"));
        bom(1, 2, "1"); bom(3, 2, "2");
        assertThat(calculate().purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(2L);
            assertThat(line.demandQty()).isEqualByComparingTo("19");
            assertThat(line.netQty()).isEqualByComparingTo("10");
            assertThat(line.reason()).contains("销售未发货", "安全库存", "BOM展开:1", "BOM展开:3");
        });
    }

    @Test
    void manufacturedProductAlsoUsedAsMaterialCanOnlyUseResidualSupply() {
        sales.put(1L, bd("6")); sales.put(2L, bd("5")); stock.put(2L, bd("3")); purchase.put(2L, bd("5"));
        bom(1, 2, "1"); bom(2, 3, "2");
        var result = calculate();
        assertThat(total(result.productionLines(), 2)).isZero();
        // MRP-lite buys expanded components instead of recursively manufacturing them.
        assertThat(result.purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(2L);
            assertThat(line.onHandQty()).isZero();
            assertThat(line.openSupplyQty()).isEqualByComparingTo("3");
            assertThat(line.netQty()).isEqualByComparingTo("3");
        });
    }

    @Test
    void materialShortageDoesNotReuseStockAllocatedToDirectProduction() {
        sales.put(1L, bd("6")); sales.put(2L, bd("5")); stock.put(2L, bd("3"));
        bom(1, 2, "1"); bom(2, 3, "2");
        var result = calculate();
        assertThat(total(result.productionLines(), 2)).isEqualByComparingTo("2");
        assertThat(total(result.purchaseLines(), 2)).isEqualByComparingTo("6");
        assertThat(total(result.purchaseLines(), 3)).isEqualByComparingTo("4");
    }

    @Test
    void preservesFractionalMaterialDemand() {
        sales.put(1L, bd("1.25")); sales.put(2L, bd("0.125")); stock.put(2L, bd("0.1")); bom(1, 2, "0.3");
        assertThat(total(calculate().purchaseLines(), 2)).isEqualByComparingTo("0.4");
    }

    @Test
    void bomExpansionUsesBaseQuantityAndLossJustLikeProductionOrders() {
        sales.put(1L, bd("6")); sales.put(2L, bd("5")); stock.put(2L, bd("5"));
        boms.put(1L, 101L);
        components.add(Map.of("bomId", 101L, "materialId", 2L, "qtyPer", bd("4"),
                "baseQty", bd("10"), "lossRate", bd("0.1")));
        assertThat(total(calculate().purchaseLines(), 2)).isEqualByComparingTo("2.64");
    }

    @Test
    void nativeQueriesAlwaysUseCurrentCompanyAndBook() {
        calculate();
        verify(jdbc, atLeastOnce()).queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId()));
        assertThat(mockingDetails(jdbc).getInvocations()).allSatisfy(call -> {
            assertThat(call.getArgument(1, Long.class)).isEqualTo(AUDIT.companyId());
            assertThat(call.getArgument(2, Long.class)).isEqualTo(AUDIT.accountBookId());
        });
    }

    @ParameterizedTest
    @CsvSource({"inv_alert_rule", "pur_order"})
    void queryFailuresAbortInsteadOfReturningIncompleteOrOverstatedPlans(String table) {
        failingQuery = table;
        assertThatThrownBy(this::calculate).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }

    private MrpPlanCalculationService.CalculationResult calculate() {
        when(jdbc.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId()))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (failingQuery != null && sql.contains("from " + failingQuery)) {
                throw new org.springframework.dao.DataAccessResourceFailureException("MRP fixture failure");
            }
            if (sql.contains("from sal_order")) return rows(sales);
            if (sql.contains("from inv_alert_rule")) return safety.entrySet().stream().map(e ->
                    Map.<String, Object>of("productId", e.getKey(), "minQty", e.getValue(),
                            "onHand", stock.getOrDefault(e.getKey(), BigDecimal.ZERO))).toList();
            if (sql.contains("from inv_balance")) return rows(stock);
            if (sql.contains("from pur_order")) return rows(purchase);
            if (sql.contains("from prd_order")) return rows(production);
            if (sql.contains("from prd_bom_line")) return components;
            if (sql.contains("from prd_bom")) return boms.entrySet().stream().map(e ->
                    Map.<String, Object>of("productId", e.getKey(), "bomId", e.getValue())).toList();
            throw new AssertionError("Unexpected query: " + sql);
        });
        return new MrpPlanCalculationService(jdbc).calculate(AUDIT);
    }
    private void bom(long product, long material, String quantity) {
        boms.put(product, product + 100);
        components.add(Map.of("bomId", product + 100, "materialId", material, "qtyPer", bd(quantity), "baseQty", BigDecimal.ONE, "lossRate", BigDecimal.ZERO));
    }
    private List<Map<String, Object>> rows(Map<Long, BigDecimal> values) {
        return values.entrySet().stream().map(e -> Map.<String, Object>of("productId", e.getKey(), "qty", e.getValue())).toList();
    }
    private BigDecimal total(List<MrpPlanCalculationService.ComputedLine> lines, long product) {
        return lines.stream().filter(l -> l.productId() == product).map(MrpPlanCalculationService.ComputedLine::netQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
