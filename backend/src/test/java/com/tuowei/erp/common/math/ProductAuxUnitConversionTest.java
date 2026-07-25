package com.tuowei.erp.common.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAuxUnitConversionTest {

    @Test
    void convertsAuxQtyToStockQty() {
        ProductAuxUnitConversion.ResolvedAuxUnit resolved = ProductAuxUnitConversion.resolve(
                null,
                new BigDecimal("2"),
                "箱",
                new BigDecimal("12")
        );
        assertThat(resolved.stockQty()).isEqualByComparingTo("24.0000");
        assertThat(resolved.auxQty()).isEqualByComparingTo("2.0000");
        assertThat(resolved.auxUnitName()).isEqualTo("箱");
        assertThat(resolved.conversionFactor()).isEqualByComparingTo("12");
    }

    @Test
    void derivesAuxQtyFromStockQty() {
        ProductAuxUnitConversion.ResolvedAuxUnit resolved = ProductAuxUnitConversion.resolve(
                new BigDecimal("24"),
                null,
                "箱",
                new BigDecimal("12")
        );
        assertThat(resolved.stockQty()).isEqualByComparingTo("24.0000");
        assertThat(resolved.auxQty()).isEqualByComparingTo("2.0000");
    }

    @Test
    void keepsBaseQtyWhenAuxDisabled() {
        ProductAuxUnitConversion.ResolvedAuxUnit resolved = ProductAuxUnitConversion.resolve(
                new BigDecimal("5"),
                null,
                null,
                null
        );
        assertThat(resolved.stockQty()).isEqualByComparingTo("5.0000");
        assertThat(resolved.auxQty()).isNull();
        assertThat(resolved.auxUnitName()).isNull();
    }

    @Test
    void rejectsAuxQtyWithoutAuxUnit() {
        assertThatThrownBy(() -> ProductAuxUnitConversion.resolve(
                new BigDecimal("1"),
                new BigDecimal("2"),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未启用辅单位");
    }
}
