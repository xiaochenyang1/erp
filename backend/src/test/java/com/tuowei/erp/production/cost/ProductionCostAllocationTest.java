package com.tuowei.erp.production.cost;

import com.tuowei.erp.production.cost.service.ProductionCostAllocation;
import com.tuowei.erp.production.cost.service.ProductionCostAmounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCostAllocationTest {

    @Test
    void allocatesEveryComponentByTheSameCompletionQuantityRatio() {
        ProductionCostAmounts costs = ProductionCostAllocation.allocateCompletion(
                new BigDecimal("1000.00"),
                new BigDecimal("300.00"),
                new BigDecimal("200.00"),
                new BigDecimal("10.0000"),
                new BigDecimal("4.0000")
        );

        assertThat(costs.material()).isEqualByComparingTo("400.00");
        assertThat(costs.labor()).isEqualByComparingTo("120.00");
        assertThat(costs.overhead()).isEqualByComparingTo("80.00");
        assertThat(costs.total()).isEqualByComparingTo("600.00");
    }

    @Test
    void finalCompletionTakesEveryRemainingCent() {
        ProductionCostAmounts costs = ProductionCostAllocation.allocateCompletion(
                new BigDecimal("333.33"),
                new BigDecimal("66.67"),
                new BigDecimal("10.01"),
                new BigDecimal("3.0000"),
                new BigDecimal("3.0000")
        );

        assertThat(costs.material()).isEqualByComparingTo("333.33");
        assertThat(costs.labor()).isEqualByComparingTo("66.67");
        assertThat(costs.overhead()).isEqualByComparingTo("10.01");
    }

    @Test
    void reversalUsesOriginalComponentMixAndAssignsRoundingToTheLastPresentComponent() {
        ProductionCostAmounts costs = ProductionCostAllocation.splitReversal(
                new BigDecimal("33.34"),
                new BigDecimal("60.00"),
                new BigDecimal("30.00"),
                new BigDecimal("10.00")
        );

        assertThat(costs.material()).isEqualByComparingTo("20.00");
        assertThat(costs.labor()).isEqualByComparingTo("10.00");
        assertThat(costs.overhead()).isEqualByComparingTo("3.34");
        assertThat(costs.total()).isEqualByComparingTo("33.34");
    }

    @Test
    void legacyReversalDefaultsToMaterialWhenNoCompositionExists() {
        ProductionCostAmounts costs = ProductionCostAllocation.splitReversal(
                new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        assertThat(costs.material()).isEqualByComparingTo("25.00");
        assertThat(costs.labor()).isZero();
        assertThat(costs.overhead()).isZero();
    }
}
