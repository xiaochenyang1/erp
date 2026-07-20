package com.tuowei.erp.finance.period;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.period.controller.AccountPeriodController;
import com.tuowei.erp.finance.period.service.AccountPeriodCloseChecker;
import com.tuowei.erp.finance.period.service.AccountPeriodService;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AccountPeriodControllerTest {

    private static final String PERIOD_VIEW = "finance:period:view";
    private static final String PERIOD_MANAGE = "finance:period:manage";
    private static final String PERIOD_CLOSE = "finance:period:close";
    private static final String PERIOD_REOPEN = "finance:period:reopen";

    @Autowired
    private AccountPeriodService accountPeriodService;

    @Autowired
    private AccountPeriodCloseChecker closeChecker;

    @Autowired
    private AccountPeriodController accountPeriodController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        deleteIfExists("fin_bank_statement", "id between 863000 and 863999");
        deleteIfExists("fin_fund_account", "id between 863000 and 863999");
        jdbcTemplate.update("delete from fin_voucher_entry where voucher_id between 863000 and 863999 or id between 863000 and 863999");
        jdbcTemplate.update("delete from fin_voucher where id between 863000 and 863999");
        jdbcTemplate.update("delete from inv_txn where id between 863000 and 863999");
        jdbcTemplate.update("delete from fin_account_period where id between 863000 and 863999 or period_year in (2036, 2037)");
    }

    @Test
    @WithErpUser(authorities = {PERIOD_MANAGE, PERIOD_VIEW})
    void generatesYearPeriodsIdempotently() {
        List<AccountPeriodResponse> generated = accountPeriodService.generate(2036);

        Assertions.assertThat(generated).hasSize(12);
        Assertions.assertThat(generated.get(0).periodMonth()).isEqualTo("2036-01");
        Assertions.assertThat(generated.get(0).startDate()).isEqualTo(LocalDate.of(2036, 1, 1));
        Assertions.assertThat(generated.get(0).endDate()).isEqualTo(LocalDate.of(2036, 1, 31));
        Assertions.assertThat(generated.get(0).status()).isEqualTo("OPEN");

        Assertions.assertThat(accountPeriodService.generate(2036)).hasSize(12);

        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_account_period
                where company_id = 1
                  and account_book_id = 1
                  and period_year = 2036
                """, Integer.class);
        Assertions.assertThat(count).isEqualTo(12);
    }

    @Test
    @WithErpUser(authorities = {PERIOD_VIEW})
    void listsPeriodsByYearInMonthOrder() {
        seedPeriod(863101L, 2037, "2037-02", "OPEN");
        seedPeriod(863102L, 2037, "2037-01", "LOCKED");

        List<AccountPeriodResponse> periods = accountPeriodService.list(2037);

        Assertions.assertThat(periods).hasSize(2);
        Assertions.assertThat(periods.get(0).periodMonth()).isEqualTo("2037-01");
        Assertions.assertThat(periods.get(0).status()).isEqualTo("LOCKED");
        Assertions.assertThat(periods.get(1).periodMonth()).isEqualTo("2037-02");
    }

    @Test
    @WithErpUser(userId = 863001L, authorities = {PERIOD_CLOSE, PERIOD_REOPEN, PERIOD_VIEW})
    void locksClosesAndReopensLatestLockedPeriod() {
        seedPeriod(863201L, 2037, "2037-03", "OPEN");

        AccountPeriodResponse locked = accountPeriodService.lock(863201L);
        Assertions.assertThat(locked.status()).isEqualTo("LOCKED");
        Assertions.assertThat(locked.lockedBy()).isEqualTo(863001L);

        AccountPeriodResponse reopened = accountPeriodService.reopen(863201L);
        Assertions.assertThat(reopened.status()).isEqualTo("OPEN");
        Assertions.assertThat(reopened.reopenedBy()).isEqualTo(863001L);

        Assertions.assertThat(accountPeriodService.lock(863201L).status()).isEqualTo("LOCKED");

        AccountPeriodResponse closed = accountPeriodService.close(863201L);
        Assertions.assertThat(closed.status()).isEqualTo("CLOSED");
        Assertions.assertThat(closed.closedBy()).isEqualTo(863001L);
    }

    @Test
    @WithErpUser(authorities = {PERIOD_CLOSE, PERIOD_REOPEN})
    void rejectsInvalidStateTransitions() {
        seedPeriod(863301L, 2037, "2037-04", "OPEN");
        seedPeriod(863302L, 2037, "2037-05", "LOCKED");

        Assertions.assertThatThrownBy(() -> accountPeriodService.close(863301L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有已锁定期间可以结账");

        Assertions.assertThatThrownBy(() -> accountPeriodService.reopen(863301L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("只有已锁定期间可以解锁");
    }

    @Test
    @WithErpUser(authorities = {PERIOD_CLOSE, PERIOD_VIEW})
    void closeCheckReportsReconciliationDifferenceAndBlocksLock() {
        seedPeriod(863401L, 2037, "2037-06", "OPEN");
        seedInventoryTxn(863501L, "PURCHASE_RECEIPT", "PR-863501", "IN", "100.00");
        seedVoucherInventoryEntry(863601L, "PURCHASE_RECEIPT", "PR-863501", "90.00", "0.00");

        AccountPeriodCloseCheckResponse check = closeChecker.check(863401L);

        Assertions.assertThat(check.passed()).isFalse();
        Assertions.assertThat(check.issues())
                .extracting("type")
                .contains("INVENTORY_FINANCE_RECONCILIATION");
        Assertions.assertThatThrownBy(() -> accountPeriodService.lock(863401L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("期间月结检查未通过，不能锁定");
    }

    @Test
    @WithErpUser(authorities = {PERIOD_VIEW})
    void returnsInventoryFinanceDifferenceDetail() {
        seedPeriod(863401L, 2037, "2037-06", "OPEN");
        seedInventoryTxn(863501L, "PURCHASE_RECEIPT", "PR-863501", "IN", "100.00");
        seedVoucherInventoryEntry(863601L, "PURCHASE_RECEIPT", "PR-863501", "90.00", "0.00");

        ApiResponse<InventoryFinanceDifferenceDetailResponse> response =
                accountPeriodController.reconciliationDifferenceDetail(863401L, "PURCHASE_RECEIPT", "PR-863501");

        InventoryFinanceDifferenceDetailResponse detail = response.data();
        Assertions.assertThat(response.code()).isEqualTo("0");
        Assertions.assertThat(detail.sourceKey()).isEqualTo("PURCHASE_RECEIPT:PR-863501");
        Assertions.assertThat(detail.inventoryAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThat(detail.financeAmount()).isEqualByComparingTo("90.00");
        Assertions.assertThat(detail.differenceAmount()).isEqualByComparingTo("10.00");
        Assertions.assertThat(detail.inventoryTransactions()).hasSize(1);
        Assertions.assertThat(detail.voucherEntries()).hasSize(1);
    }

    @Test
    @WithErpUser(authorities = {PERIOD_CLOSE, PERIOD_VIEW})
    void closeCheckReportsUnmatchedBankStatementsAndBlocksLock() {
        seedPeriod(863701L, 2037, "2037-07", "OPEN");
        seedFundAccount(863801L);
        seedBankStatement(863901L, 863801L, "UNMATCHED");

        AccountPeriodCloseCheckResponse check = closeChecker.check(863701L);

        Assertions.assertThat(check.passed()).isFalse();
        Assertions.assertThat(check.issues())
                .extracting("type")
                .contains("BANK_STATEMENT_UNMATCHED");
        Assertions.assertThat(check.issues())
                .extracting("message")
                .contains("存在未匹配银行流水");
        Assertions.assertThatThrownBy(() -> accountPeriodService.lock(863701L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("期间月结检查未通过，不能锁定");
    }

    private void seedPeriod(long id, int year, String periodMonth, String status) {
        LocalDate start = LocalDate.parse(periodMonth + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, 0, ?, 0, ?, 0)
                """,
                id,
                year,
                periodMonth,
                start,
                end,
                status,
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private void seedInventoryTxn(long id, String bizType, String bizNo, String direction, String amount) {
        jdbcTemplate.update("""
                insert into inv_txn
                (id, company_id, account_book_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id,
                 direction, qty, amount, unit_cost, occurred_time, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, 1001, 2001, ?, ?, ?, ?, 1.0000, ?, ?, ?, 'close check test', 0, ?, 0, ?, 0)
                """,
                id,
                bizType,
                bizNo,
                id,
                direction,
                new java.math.BigDecimal(amount),
                new java.math.BigDecimal(amount).abs(),
                LocalDateTime.of(2037, 6, 18, 10, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private void seedVoucherInventoryEntry(long id, String sourceType, String sourceNo, String debit, String credit) {
        jdbcTemplate.update("""
                insert into fin_voucher
                (id, company_id, account_book_id, voucher_no, source_type, source_id, source_no, biz_date, amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, 'POSTED', 0, 'close check test', 0, ?, 0, ?, 0)
                """,
                id,
                "VO-" + id,
                sourceType,
                id,
                sourceNo,
                LocalDate.of(2037, 6, 18),
                new java.math.BigDecimal(debit).add(new java.math.BigDecimal(credit)),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));

        jdbcTemplate.update("""
                insert into fin_voucher_entry
                (id, company_id, account_book_id, voucher_id, biz_date, line_no, subject_id, subject_code, subject_name,
                 debit_amount, credit_amount, summary, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, 1, 910001, '1001', '库存商品', ?, ?, 'close check entry', 0, ?, 0, ?, 0)
                """,
                id + 100,
                id,
                LocalDate.of(2037, 6, 18),
                new java.math.BigDecimal(debit),
                new java.math.BigDecimal(credit),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private void seedFundAccount(long id) {
        jdbcTemplate.update("""
                insert into fin_fund_account
                (id, company_id, account_book_id, account_code, account_name, account_type, currency_code,
                 opening_balance, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, '月结测试账户', 'BANK', 'CNY',
                        0, 'ENABLED', 0, 'period close fund test', 0, ?, 0, ?, 0)
                """,
                id,
                "PERIOD_FUND_" + id,
                LocalDateTime.of(2026, 5, 26, 9, 0),
                LocalDateTime.of(2026, 5, 26, 9, 0));
    }

    private void seedBankStatement(long id, long accountId, String status) {
        jdbcTemplate.update("""
                insert into fin_bank_statement
                (id, company_id, account_book_id, fund_account_id, statement_no, external_txn_no,
                 transaction_date, direction, amount, counterparty_name, summary, status,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, '2037-07-12', 'IN', 100.00, '月结客户',
                        '未匹配银行流水', ?, 0, 'period close fund test', 0, ?, 0, ?, 0)
                """,
                id,
                accountId,
                "BS-" + id,
                "EXT-" + id,
                status,
                LocalDateTime.of(2026, 5, 26, 9, 0),
                LocalDateTime.of(2026, 5, 26, 9, 0));
    }

    private void deleteIfExists(String tableName, String condition) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select count(*) > 0
                from information_schema.tables
                where table_schema = database()
                  and table_name = ?
                """, Boolean.class, tableName.toUpperCase());
        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.update("delete from " + tableName + " where " + condition);
        }
    }
}
