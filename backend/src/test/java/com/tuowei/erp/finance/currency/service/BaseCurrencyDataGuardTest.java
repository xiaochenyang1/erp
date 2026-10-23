package com.tuowei.erp.finance.currency.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseCurrencyDataGuardTest {
    @Test
    void allowsChangingBaseCurrencyWhenTheAccountBookHasNoMoneyData() {
        Database database = database(true);
        try {
            database.guard().requireNoCurrencySensitiveData(9L, 2L);
        } finally {
            database.close();
        }
    }

    @Test
    void rejectsChangingBaseCurrencyWhenTheAccountBookHasMoneyData() {
        Database database = database(true);
        try {
            database.jdbc().update("insert into fin_payable (id, company_id, account_book_id, original_amount)"
                    + " values (1, 9, 2, 100)");

            assertThatThrownBy(() -> database.guard().requireNoCurrencySensitiveData(9L, 2L))
                    .isInstanceOf(BusinessConflictException.class)
                    .hasMessageContaining("不能变更本位币");
        } finally {
            database.close();
        }
    }

    @Test
    void ignoresMoneyDataFromAnotherAccountBook() {
        Database database = database(true);
        try {
            database.jdbc().update("insert into fin_payable (id, company_id, account_book_id, original_amount)"
                    + " values (1, 9, 99, 100)");

            database.guard().requireNoCurrencySensitiveData(9L, 2L);
        } finally {
            database.close();
        }
    }

    @Test
    void failsClosedWhenTheVoucherTableCannotBeDiscovered() {
        Database database = database(false);
        try {
            assertThatThrownBy(() -> database.guard().requireNoCurrencySensitiveData(9L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无法核实账套凭证数据");
        } finally {
            database.close();
        }
    }

    private Database database(boolean includeVoucherTable) {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:base_currency_guard_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        if (includeVoucherTable) {
            jdbc.execute("create table fin_voucher (id bigint, company_id bigint, account_book_id bigint, amount decimal(18, 2))");
        }
        jdbc.execute("create table fin_payable (id bigint, company_id bigint, account_book_id bigint, original_amount decimal(18, 2))");
        return new Database(jdbc, new BaseCurrencyDataGuard(jdbc));
    }

    private record Database(JdbcTemplate jdbc, BaseCurrencyDataGuard guard) {
        private void close() {
            jdbc.execute("SHUTDOWN");
        }
    }
}
