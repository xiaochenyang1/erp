package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCostAccountingMigrationTest {

    @Test
    void upgradesHistoricalMaterialOnlyProductionWithoutChangingPublishedTotals() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:production_cost_v171;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        source.setDriverClassName("org.h2.Driver");
        var directory = H2MigrationTestSupport.copyCompatibleMigrations(
                ProductionCostAccountingMigrationTest.class,
                "production-cost-v171"
        );
        String location = "filesystem:" + directory.toAbsolutePath().toString().replace('\\', '/');
        Flyway.configure().dataSource(source).locations(location).target("170").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("""
                insert into prd_order
                (id, company_id, account_book_id, order_no, bom_id, product_id,
                 material_warehouse_id, finished_warehouse_id, planned_qty, completed_qty,
                 planned_start_date, planned_finish_date, status, issued_amount, finished_amount)
                values (997171001, 1, 1, 'MO-HIST-171', 1, 1, 1, 2, 10.0000, 8.0000,
                        '2026-01-01', '2026-01-02', 'MATERIAL_ISSUED', 100.00, 80.00)
                """);
        jdbc.update("""
                insert into prd_completion
                (id, company_id, account_book_id, completion_no, order_id, completion_date, completed_qty, completed_amount)
                values (997171002, 1, 1, 'PC-HIST-171', 997171001, '2026-01-02', 8.0000, 80.00)
                """);
        jdbc.update("""
                insert into prd_completion_reversal
                (id, company_id, account_book_id, reversal_no, order_id, reversal_date, reversed_qty, reversed_amount)
                values (997171003, 1, 1, 'PCR-HIST-171', 997171001, '2026-01-03', 2.0000, 20.00)
                """);

        Flyway flyway = Flyway.configure().dataSource(source).locations(location).load();
        flyway.migrate();

        assertThat(jdbc.queryForObject(
                "select finished_material_amount from prd_order where id = 997171001",
                BigDecimal.class
        )).isEqualByComparingTo("80.00");
        assertThat(jdbc.queryForObject(
                "select material_cost_amount from prd_completion where id = 997171002",
                BigDecimal.class
        )).isEqualByComparingTo("80.00");
        assertThat(jdbc.queryForObject(
                "select material_cost_amount from prd_completion_reversal where id = 997171003",
                BigDecimal.class
        )).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'prd_cost_entry'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "select subject_code from fin_account_subject where subject_code in ('2211', '5101') order by subject_code",
                String.class
        )).containsExactly("2211", "5101");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_menu where permission = 'production:order:cost'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'prd_cost_entry' "
                        + "and column_name in ('reversal_of_entry_id', 'correction_of_entry_id')",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_menu where permission = 'production:order:cost:reverse'",
                Integer.class
        )).isEqualTo(1);
        jdbc.update("""
                insert into prd_cost_entry
                (id, company_id, account_book_id, cost_no, order_id, cost_type, business_date,
                 amount, status, deleted_flag, created_by, updated_by, version)
                values (997172001, 1, 1, 'PCE-HIST-172', 997171001, 'LABOR', '2026-01-03',
                        20.00, 'REVERSED', 0, 0, 0, 0)
                """);
        jdbc.update("""
                insert into prd_cost_entry
                (id, company_id, account_book_id, cost_no, order_id, cost_type, business_date,
                 amount, status, reversal_of_entry_id, deleted_flag, created_by, updated_by, version)
                values (997172002, 1, 1, 'PCE-REV-172', 997171001, 'LABOR', '2026-01-04',
                        -20.00, 'POSTED', 997172001, 0, 0, 0, 0)
                """);
        assertThat(jdbc.queryForObject(
                "select sum(amount) from prd_cost_entry where id in (997172001, 997172002)",
                BigDecimal.class
        )).isEqualByComparingTo("0.00");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        jdbc.execute("shutdown");
    }
}
