package com.tuowei.erp.finance.currency.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("testcontainers")
class BaseCurrencyDataGuardMysqlIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "erp.testcontainers.enabled", matches = "true")
    void discoversMysqlTablesAndScopesCurrencyDataByAccountBook() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("erp_currency_guard")
                .withUsername("erp")
                .withPassword("erp")) {
            mysql.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
            jdbc.execute("create table fin_voucher (id bigint primary key, company_id bigint not null,"
                    + " account_book_id bigint not null, amount decimal(18, 2))");
            jdbc.execute("create table fin_payable (id bigint primary key, company_id bigint not null,"
                    + " account_book_id bigint not null, original_amount decimal(18, 2))");
            var guard = new BaseCurrencyDataGuard(jdbc);

            guard.requireNoCurrencySensitiveData(9L, 2L);

            jdbc.update("insert into fin_payable (id, company_id, account_book_id, original_amount)"
                    + " values (1, 9, 99, 100)");
            guard.requireNoCurrencySensitiveData(9L, 2L);

            jdbc.update("insert into fin_payable (id, company_id, account_book_id, original_amount)"
                    + " values (2, 9, 2, 100)");
            assertThatThrownBy(() -> guard.requireNoCurrencySensitiveData(9L, 2L))
                    .isInstanceOf(BusinessConflictException.class)
                    .hasMessageContaining("不能变更本位币");

            jdbc.execute("drop table fin_voucher");
            assertThatThrownBy(() -> guard.requireNoCurrencySensitiveData(9L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无法核实账套凭证数据");
        }
    }
}
