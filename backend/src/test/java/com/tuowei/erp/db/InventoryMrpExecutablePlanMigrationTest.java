package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMrpExecutablePlanMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inventory_mrp_executable;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventoryMrpExecutablePlanMigrationTest.class,
                "inventory-mrp-executable-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v129CreatesMrpRunTablesAndConvertPermission() {
        List<String> runColumns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'inv_mrp_run'
                """, String.class);
        assertThat(runColumns).contains("run_no", "status", "purchase_count", "production_count");

        List<String> lineColumns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'inv_mrp_run_line'
                """, String.class);
        assertThat(lineColumns).contains("suggestion_type", "net_qty", "converted_biz_no", "status");

        Integer menu = jdbcTemplate.queryForObject("""
                select count(*) from sys_menu where menu_code = 'INVENTORY_MRP_CONVERT'
                """, Integer.class);
        assertThat(menu).isEqualTo(1);

        Integer role = jdbcTemplate.queryForObject("""
                select count(*) from sys_role_menu where menu_id = 5440 and role_id = 3002
                """, Integer.class);
        assertThat(role).isEqualTo(1);
    }
}
