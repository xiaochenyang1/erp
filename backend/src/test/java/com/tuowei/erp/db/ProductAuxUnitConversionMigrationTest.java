package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAuxUnitConversionMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:product_aux_unit_conversion;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                ProductAuxUnitConversionMigrationTest.class,
                "product-aux-unit-conversion-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v141AddsAuxUnitAndConversionFactorColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'md_product'
                """, String.class).stream().map(String::toLowerCase).toList();
        assertThat(columns).contains("aux_unit_name", "conversion_factor");
    }
}
