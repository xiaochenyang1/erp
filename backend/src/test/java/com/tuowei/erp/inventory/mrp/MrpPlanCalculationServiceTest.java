package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MrpPlanCalculationServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 13, 11, 30)
    );

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void calculateCombinesSalesSafetyStockOffsetsSupplyAndExpandsBomMaterials() {
        when(jdbcTemplate.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(
                        List.of(Map.of("productId", 1001L, "qty", new BigDecimal("10"))),
                        List.of(Map.of(
                                "productId", 1001L,
                                "minQty", new BigDecimal("12"),
                                "onHand", new BigDecimal("2")
                        )),
                        List.of(
                                Map.of("productId", 1001L, "qty", new BigDecimal("3")),
                                Map.of("productId", 1002L, "qty", new BigDecimal("1"))
                        ),
                        List.of(
                                Map.of("productId", 1001L, "qty", new BigDecimal("2")),
                                Map.of("productId", 1002L, "qty", new BigDecimal("1"))
                        ),
                        List.of(Map.of("productId", 1001L, "qty", new BigDecimal("1"))),
                        List.of(Map.of("productId", 1001L, "bomId", 5001L)),
                        List.of(Map.of("bomId", 5001L, "materialId", 1002L, "qtyPer", new BigDecimal("2")))
                );

        var result = service().calculate(AUDIT);

        assertThat(result.productionLines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(1001L);
            assertThat(line.netQty()).isEqualByComparingTo("14.0000");
            assertThat(line.bomId()).isEqualTo(5001L);
            assertThat(line.reason()).contains("销售未发货", "安全库存", "有BOM建议生产");
        });
        assertThat(result.purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(1002L);
            assertThat(line.demandQty()).isEqualByComparingTo("28.0000");
            assertThat(line.openSupplyQty()).isEqualByComparingTo("1.0000");
            assertThat(line.netQty()).isEqualByComparingTo("26.0000");
            assertThat(line.reason()).contains("BOM展开:1001");
        });
    }

    @Test
    void calculateDoesNotCreateSuggestionsWhenSupplyCoversDemand() {
        when(jdbcTemplate.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(
                        List.of(Map.of("productId", 1001L, "qty", new BigDecimal("5"))),
                        List.of(),
                        List.of(Map.of("productId", 1001L, "qty", new BigDecimal("5"))),
                        List.of(),
                        List.of(),
                        List.of()
                );

        var result = service().calculate(AUDIT);

        assertThat(result.productionLines()).isEmpty();
        assertThat(result.purchaseLines()).isEmpty();
    }

    @Test
    void calculateUsesTenantAndAccountBookArgumentsForNativeQueries() {
        when(jdbcTemplate.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        service().calculate(AUDIT);

        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
                .queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId()));
    }

    private MrpPlanCalculationService service() {
        return new MrpPlanCalculationService(jdbcTemplate);
    }
}
