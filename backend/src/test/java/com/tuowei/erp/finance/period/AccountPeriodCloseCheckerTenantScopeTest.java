package com.tuowei.erp.finance.period;

import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.AccountPeriodCloseChecker;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountPeriodCloseCheckerTenantScopeTest {

    @Test
    void settlementAllocationChecksScopeAllocationRowsByParentCompanyAndAccountBook() {
        AccountPeriodMapper accountPeriodMapper = mock(AccountPeriodMapper.class);
        InventoryFinanceReconciliationService reconciliationService = mock(InventoryFinanceReconciliationService.class);
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AccountPeriodEntity period = period();
        when(accountPeriodMapper.selectById(period.getId())).thenReturn(period);
        when(reconciliationService.summary(period.getId())).thenReturn(new InventoryFinanceReconciliationResponse(
                period.getId(),
                period.getPeriodMonth(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true
        ));

        new AccountPeriodCloseChecker(accountPeriodMapper, reconciliationService, jdbcTemplate).check(period.getId());

        assertThat(singleSqlContaining(jdbcTemplate.sqls(), "from fin_payment_allocation a"))
                .contains("a.company_id = p.company_id")
                .contains("a.account_book_id = p.account_book_id");
        assertThat(singleSqlContaining(jdbcTemplate.sqls(), "from fin_receipt_allocation a"))
                .contains("a.company_id = r.company_id")
                .contains("a.account_book_id = r.account_book_id");
    }

    private AccountPeriodEntity period() {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(860101L);
        period.setCompanyId(101L);
        period.setAccountBookId(202L);
        period.setPeriodYear(2026);
        period.setPeriodMonth("2026-06");
        period.setStartDate(LocalDate.of(2026, 6, 1));
        period.setEndDate(LocalDate.of(2026, 6, 30));
        period.setStatus("OPEN");
        return period;
    }

    private String singleSqlContaining(List<String> sqls, String token) {
        return sqls.stream()
                .filter(sql -> sql.contains(token))
                .reduce((left, right) -> {
                    throw new AssertionError("Expected one SQL containing " + token + ", but found more than one");
                })
                .orElseThrow(() -> new AssertionError("No SQL containing " + token));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final List<String> sqls = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sqls.add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim());
            return requiredType.cast(0L);
        }

        private List<String> sqls() {
            return sqls;
        }
    }
}
