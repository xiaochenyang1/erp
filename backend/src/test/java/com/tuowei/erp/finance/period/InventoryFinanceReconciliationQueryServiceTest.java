package com.tuowei.erp.finance.period;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryFinanceReconciliationQueryServiceTest {

    @Test
    void rejectsCrossAccountBookPeriodBeforeRunningNativeQueries() {
        AccountPeriodMapper periodMapper = mock(AccountPeriodMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AccountPeriodEntity period = period(10L, 999L);
        when(periodMapper.selectById(period.getId())).thenReturn(period);
        when(auditFactory.current()).thenReturn(audit(10L, 20L));
        InventoryFinanceReconciliationQueryService service =
                new InventoryFinanceReconciliationQueryService(periodMapper, auditFactory, jdbcTemplate);

        assertThatThrownBy(() -> service.loadSummary(period.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计期间不存在");

        assertThat(jdbcTemplate.arguments()).isEmpty();
    }

    @Test
    void detailTrimsSourceIdentityBeforeApplyingItToBothTenantScopedQueries() {
        AccountPeriodMapper periodMapper = mock(AccountPeriodMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AccountPeriodEntity period = period(10L, 20L);
        when(periodMapper.selectById(period.getId())).thenReturn(period);
        when(auditFactory.current()).thenReturn(audit(10L, 20L));
        InventoryFinanceReconciliationQueryService service =
                new InventoryFinanceReconciliationQueryService(periodMapper, auditFactory, jdbcTemplate);

        var data = service.loadDifferenceDetail(period.getId(), " SALES_DELIVERY ", " SD-1 ");

        assertThat(data.sourceType()).isEqualTo("SALES_DELIVERY");
        assertThat(data.sourceNo()).isEqualTo("SD-1");
        assertThat(jdbcTemplate.arguments()).hasSize(2);
        assertThat(jdbcTemplate.arguments()).allSatisfy(arguments ->
                assertThat(arguments).endsWith("SALES_DELIVERY", "SD-1"));
    }

    @Test
    void detailRejectsBlankSourceIdentityBeforeRunningNativeQueries() {
        AccountPeriodMapper periodMapper = mock(AccountPeriodMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AccountPeriodEntity period = period(10L, 20L);
        when(periodMapper.selectById(period.getId())).thenReturn(period);
        when(auditFactory.current()).thenReturn(audit(10L, 20L));
        InventoryFinanceReconciliationQueryService service =
                new InventoryFinanceReconciliationQueryService(periodMapper, auditFactory, jdbcTemplate);

        assertThatThrownBy(() -> service.loadDifferenceDetail(period.getId(), " ", "SD-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("来源类型不能为空");

        assertThat(jdbcTemplate.arguments()).isEmpty();
    }

    private AccountPeriodEntity period(Long companyId, Long accountBookId) {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(865001L);
        period.setCompanyId(companyId);
        period.setAccountBookId(accountBookId);
        period.setPeriodYear(2036);
        period.setPeriodMonth("2036-05");
        period.setStartDate(LocalDate.of(2036, 5, 1));
        period.setEndDate(LocalDate.of(2036, 5, 31));
        return period;
    }

    private AuditMetadata audit(Long companyId, Long accountBookId) {
        return new AuditMetadata(1L, companyId, accountBookId, LocalDateTime.of(2026, 8, 27, 9, 0));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final List<Object[]> arguments = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            arguments.add(args);
            return List.of();
        }

        private List<Object[]> arguments() {
            return arguments;
        }
    }
}
