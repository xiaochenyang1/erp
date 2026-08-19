package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLotGenealogyMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inventory_lot_genealogy;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventoryLotGenealogyMigrationTest.class,
                "inventory-lot-genealogy-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v145SeedsGenealogyMenuAndErpAdminBinding() {
        var menu = jdbcTemplate.queryForMap("""
                select id, parent_id, menu_code, path, component, permission
                from sys_menu
                where id = 5480 and deleted_flag = 0
                """);

        assertThat(menu)
                .containsEntry("id", 5480L)
                .containsEntry("parent_id", 5009L)
                .containsEntry("menu_code", "INVENTORY_LOT_GENEALOGY")
                .containsEntry("path", "/inventory/lot-genealogy")
                .containsEntry("component", "inventory/lot-genealogy/index")
                .containsEntry("permission", "inventory:lot:genealogy");

        Integer binding = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu
                where id = 7490 and role_id = 3002 and menu_id = 5480
                """, Integer.class);
        assertThat(binding).isEqualTo(1);
    }
}
