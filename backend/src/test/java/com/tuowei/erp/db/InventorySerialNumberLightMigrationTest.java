package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventorySerialNumberLightMigrationTest {
    private static JdbcTemplate jdbcTemplate;
    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inventory_serial_light;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventorySerialNumberLightMigrationTest.class, "inventory-serial-light-migrations");
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }
    @Test
    void v132CreatesSerialTableAndProductFlag() {
        Integer col = jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_name='md_product' and column_name='serial_controlled'", Integer.class);
        Integer table = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name='inv_serial_number'", Integer.class);
        Integer menu = jdbcTemplate.queryForObject("select count(*) from sys_menu where menu_code in ('INVENTORY_SERIAL','INVENTORY_SERIAL_MANAGE')", Integer.class);
        assertThat(col).isEqualTo(1);
        assertThat(table).isEqualTo(1);
        assertThat(menu).isEqualTo(2);
    }
}
