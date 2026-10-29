package com.tuowei.erp.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FxRevaluationBasisMigrationTest {
    @Test
    void upgradePreservesOldRunsWithoutInventingABasisAndCanStoreLargeSnapshots() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:fx_basis_migration_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(datasource);
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V166__fx_revaluation.sql")).execute(datasource);
            jdbc.update("""
                    insert into fin_fx_revaluation
                        (id, company_id, account_book_id, period_id, status, open_original_total,
                         ar_adjustment, ap_adjustment, revaluation_date, reversal_date)
                    values (1, 9, 2, 11, 'POSTED', 100, 20, 0, date '2026-06-30', date '2026-07-01')
                    """);

            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V167__fx_revaluation_basis.sql")).execute(datasource);

            assertThat(jdbc.queryForObject("select ar_adjustment from fin_fx_revaluation", BigDecimal.class)).isEqualByComparingTo("20");
            assertThat(jdbc.queryForObject("select status from fin_fx_revaluation", String.class)).isEqualTo("POSTED");
            assertThat(jdbc.queryForObject("select basis_hash from fin_fx_revaluation", String.class)).isNull();
            assertThat(jdbc.queryForObject("select basis_json from fin_fx_revaluation", String.class)).isNull();
            String basis = "{\"items\":[" + "{\"currency\":\"USD\",\"openOriginal\":\"100\"},".repeat(1000) + "{}]}";
            jdbc.update("update fin_fx_revaluation set basis_hash = ?, basis_json = ? where id = 1", "a".repeat(64), basis);
            assertThat(jdbc.queryForObject("select basis_json from fin_fx_revaluation", String.class)).isEqualTo(basis);
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
