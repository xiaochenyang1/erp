package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesDeliveryAcceptanceMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:sales_delivery_acceptance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                SalesDeliveryAcceptanceMigrationTest.class, "sales-delivery-acceptance-migrations");
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v152AddsDeliveryAcceptanceColumnsAndIndex() {
        assertThat(columnCount("sal_delivery", "delivered_by")).isEqualTo(1);
        assertThat(columnCount("sal_delivery", "delivered_time")).isEqualTo(1);
        assertThat(columnCount("sal_delivery", "delivery_proof_attachment_id")).isEqualTo(1);
        assertThat(indexCount("idx_sal_delivery_company_book_delivered_time")).isEqualTo(1);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name=? and column_name=?",
                Integer.class, table, column);
    }

    private int indexCount(String index) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.indexes where index_name=?",
                Integer.class, index);
    }
}
