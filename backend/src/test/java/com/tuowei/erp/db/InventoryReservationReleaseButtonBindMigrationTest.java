package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

/**
 * 回归守卫:V103 手工释放预占按钮(5018 / inventory:reservation:release)必须已绑定 ERP_ADMIN(3002)。
 * 背景:BUTTON 节点 5018 自 V33 起就存在且后端 release 端点已挂 @PreAuthorize,但从未写入 sys_role_menu。
 * SUPER_ADMIN 走反射全权限不受影响,但非超管角色权限来自 sys_role_menu;若绑定缺失,前端库存查询页
 * "释放/手工释放" 按钮挂 v-permission 后会对 ERP_ADMIN 直接隐藏。此测试锁死绑定,避免种子回退。
 */
class InventoryReservationReleaseButtonBindMigrationTest {

    private static final long ERP_ADMIN_ROLE_ID = 3002L;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inv_reservation_release_bind;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventoryReservationReleaseButtonBindMigrationTest.class,
                "inv-reservation-release-bind-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void releaseButtonNodeExistsUnderReservationMenu() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where id = 5018
                  and menu_type = 'BUTTON'
                  and permission = 'inventory:reservation:release'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void releaseButtonBoundToErpAdmin() {
        Long boundCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.permission = 'inventory:reservation:release'
                """, Long.class, ERP_ADMIN_ROLE_ID);
        Assertions.assertThat(boundCount).isEqualTo(1L);
    }

}
