package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseLocationLightMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:warehouse_location_light;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                WarehouseLocationLightMigrationTest.class,
                "warehouse-location-light-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v130CreatesLocationTableAndPermissionSeeds() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'md_location'
                """, String.class);
        assertThat(columns).contains("warehouse_id", "location_code", "is_default", "status");

        Integer menus = jdbcTemplate.queryForObject("""
                select count(*) from sys_menu
                where menu_code in ('MASTERDATA_LOCATION', 'MASTERDATA_LOCATION_MANAGE')
                """, Integer.class);
        assertThat(menus).isEqualTo(2);
    }
}
