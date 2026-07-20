package com.tuowei.erp.system.observability;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservabilityBusinessHealthServiceTest {

    private final ReadinessItemMapper readinessItemMapper = mock(ReadinessItemMapper.class);
    private final ImportJobMapper importJobMapper = mock(ImportJobMapper.class);
    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
    private final AccountPeriodMapper accountPeriodMapper = mock(AccountPeriodMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @Test
    void returnsUpWhenAllCountsAreHealthy() {
        stubAudit();
        stubCounts(0L, 0L, 0L, 1L);

        BusinessHealthResponse response = service().current();

        assertThat(response.overallStatus()).isEqualTo("UP");
        assertThat(response.generatedAt()).isEqualTo(LocalDateTime.parse("2026-06-05T08:00:00"));
        assertThat(response.checks()).hasSize(4);
        assertThat(response.checks()).allMatch(check -> "UP".equals(check.status()));
    }

    @Test
    void returnsWarnWhenAnyCheckIsUnhealthy() {
        stubAudit();
        stubCounts(2L, 1L, 3L, 0L);

        BusinessHealthResponse response = service().current();

        assertThat(response.overallStatus()).isEqualTo("WARN");
        assertThat(response.checks())
                .extracting("code")
                .containsExactly(
                        "READINESS_UNPASSED_P0_P1",
                        "IMPORT_FAILED_RECENT",
                        "NEGATIVE_INVENTORY_BALANCE",
                        "OPEN_PERIOD_COUNT"
                );
        assertThat(response.checks())
                .filteredOn(check -> "WARN".equals(check.status()))
                .hasSize(4);
    }

    private ObservabilityBusinessHealthService service() {
        return new ObservabilityBusinessHealthService(
                readinessItemMapper,
                importJobMapper,
                inventoryBalanceMapper,
                accountPeriodMapper,
                auditMetadataFactory
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current())
                .thenReturn(new AuditMetadata(1001L, 1L, 1L, LocalDateTime.parse("2026-06-05T08:00:00")));
    }

    private void stubCounts(Long readiness, Long imports, Long negativeInventory, Long openPeriods) {
        when(readinessItemMapper.selectCount(any())).thenReturn(readiness);
        when(importJobMapper.selectCount(any())).thenReturn(imports);
        when(inventoryBalanceMapper.selectCount(any())).thenReturn(negativeInventory);
        when(accountPeriodMapper.selectCount(any())).thenReturn(openPeriods);
    }
}
