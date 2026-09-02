package com.tuowei.erp.finance.margin;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.margin.service.GrossMarginService;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrossMarginServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Test
    void usesPostedDeliveryInventoryCostInsteadOfPurchasePrice() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                1L, 10L, 20L, LocalDateTime.of(2026, 7, 25, 10, 0)
        ));
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(20L), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)))).thenReturn(List.of(
                Map.of(
                        "productId", 100L,
                        "productCode", "P-1",
                        "productName", "Product 1",
                        "salesQty", new BigDecimal("2.0000"),
                        "salesAmount", new BigDecimal("200.00"),
                        "costAmount", new BigDecimal("70.00")
                )
        ));

        GrossMarginService service = new GrossMarginService(jdbcTemplate, auditMetadataFactory);
        GrossMarginSummaryResponse summary = service.summary(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(10L), eq(20L), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)));
        assertThat(sqlCaptor.getValue()).contains("inv_txn");
        assertThat(sqlCaptor.getValue()).contains("SALES_DELIVERY");
        assertThat(sqlCaptor.getValue()).contains("t.direction = 'OUT'");
        assertThat(sqlCaptor.getValue()).contains("d.company_id = ?");
        assertThat(sqlCaptor.getValue()).contains("d.account_book_id = ?");
        assertThat(sqlCaptor.getValue()).contains("p.company_id = d.company_id");
        assertThat(sqlCaptor.getValue()).contains("p.account_book_id = d.account_book_id");
        assertThat(sqlCaptor.getValue()).contains("d.status = 'POSTED'");
        assertThat(sqlCaptor.getValue()).doesNotContain("purchase_price");

        assertThat(summary.salesAmount()).isEqualByComparingTo("200.00");
        assertThat(summary.costAmount()).isEqualByComparingTo("70.00");
        assertThat(summary.grossMargin()).isEqualByComparingTo("130.00");
        assertThat(summary.lines()).hasSize(1);
        assertThat(summary.lines().get(0).costAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void rejectsInvalidDateRangeBeforeAccessingTenantOrDatabase() {
        GrossMarginService service = new GrossMarginService(jdbcTemplate, auditMetadataFactory);

        assertThatThrownBy(() -> service.summary(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("日期区间不合法");

        verifyNoInteractions(auditMetadataFactory, jdbcTemplate);
    }
}
