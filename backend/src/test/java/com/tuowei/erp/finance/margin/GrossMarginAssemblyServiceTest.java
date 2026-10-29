package com.tuowei.erp.finance.margin;

import com.tuowei.erp.finance.margin.service.GrossMarginAssemblyService;
import com.tuowei.erp.finance.margin.service.GrossMarginQueryService;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrossMarginAssemblyServiceTest {

    private final GrossMarginAssemblyService service = new GrossMarginAssemblyService();

    @Test
    void zeroSalesAmountProducesZeroMarginRate() {
        GrossMarginSummaryResponse summary = service.assemble(data(List.of(Map.of(
                "productId", 1L,
                "salesQty", BigDecimal.ZERO,
                "salesAmount", BigDecimal.ZERO,
                "costAmount", new BigDecimal("10.00")
        ))));

        assertThat(summary.marginRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.lines().get(0).marginRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.grossMargin()).isEqualByComparingTo("-10.00");
    }

    @Test
    void roundsLinesBeforeCalculatingPreciseTotalsAndRates() {
        GrossMarginSummaryResponse summary = service.assemble(data(List.of(
                Map.of(
                        "productId", 1L,
                        "productCode", "P-1",
                        "productName", "Product 1",
                        "salesQty", new BigDecimal("1.23456"),
                        "salesAmount", new BigDecimal("100.005"),
                        "costAmount", new BigDecimal("30.004")
                ),
                Map.of(
                        "productId", 2L,
                        "productCode", "P-2",
                        "productName", "Product 2",
                        "salesQty", new BigDecimal("2.00004"),
                        "salesAmount", new BigDecimal("50.004"),
                        "costAmount", new BigDecimal("20.006")
                )
        )));

        assertThat(summary.salesAmount()).isEqualByComparingTo("150.01");
        assertThat(summary.costAmount()).isEqualByComparingTo("50.01");
        assertThat(summary.grossMargin()).isEqualByComparingTo("100.00");
        assertThat(summary.marginRate()).isEqualByComparingTo("66.66");
        assertThat(summary.lines().get(0).salesQty()).isEqualByComparingTo("1.2346");
        assertThat(summary.lines().get(0).grossMargin()).isEqualByComparingTo("70.01");
        assertThat(summary.lines().get(1).costAmount()).isEqualByComparingTo("20.01");
        assertThat(summary.lines().get(1).marginRate()).isEqualByComparingTo("59.98");
    }

    private GrossMarginQueryService.GrossMarginData data(List<Map<String, Object>> rows) {
        return new GrossMarginQueryService.GrossMarginData(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                rows
        );
    }
}
