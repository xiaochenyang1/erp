package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLocationPostingMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inventory_location_posting;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventoryLocationPostingMigrationTest.class,
                "inventory-location-posting-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v131AddsLocationIdColumnsAndIndexes() {
        for (String table : List.of("inv_balance", "inv_txn", "inv_lot_balance")) {
            Integer count = jdbcTemplate.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_name = ? and column_name = 'location_id'
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(1);
        }
    }
}
