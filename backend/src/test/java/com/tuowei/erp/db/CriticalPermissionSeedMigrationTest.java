package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

/**
 * 回归守卫：V124 必须让 ERP_ADMIN 获得三个已由 Controller 强制校验的权限，
 * 并把早期生成的库存预警处置按钮迁到真实的库存预警菜单下。
 */
class CriticalPermissionSeedMigrationTest {

    private static final long ERP_ADMIN_ROLE_ID = 3002L;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:critical_permission_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                CriticalPermissionSeedMigrationTest.class,
                "critical-permission-seed-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void missingControllerPermissionsAreSeededUnderTheirRealMenus() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and ((parent_id = 5146 and permission = 'inventory:alert:create')
                    or (parent_id = 5147 and permission = 'finance:receipt:view')
                    or (parent_id = 5148 and permission = 'system:sequence-rule:view'))
                """, Long.class);

        Assertions.assertThat(count).isEqualTo(3L);
    }

    @Test
    void allMissingPermissionsAreBoundToErpAdmin() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.permission in (
                    'inventory:alert:create',
                    'finance:receipt:view',
                    'system:sequence-rule:view'
                  )
                """, Long.class, ERP_ADMIN_ROLE_ID);

        Assertions.assertThat(count).isEqualTo(3L);
    }

    @Test
    void inventoryAlertHandleButtonIsAttachedToTheCurrentAlertMenu() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where parent_id = 5146
                  and menu_type = 'BUTTON'
                  and permission = 'inventory:alert:handle'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);

        Assertions.assertThat(count).isEqualTo(1L);
    }
}
