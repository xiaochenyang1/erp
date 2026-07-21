package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

/**
 * 回归守卫:V102 按钮级权限收口种子必须已落库并绑定 ERP_ADMIN(3002)。
 * 背景:后端把三处写操作从只读码收紧为独立写权限码(notification:manage / workflow:approve / workflow:reject)。
 * SUPER_ADMIN 走反射全权限不受影响,但非超管角色权限来自 sys_role_menu;若种子缺失,ERP_ADMIN 点
 * "标记已读/审批/驳回" 会直接 403。此测试锁死 BUTTON 节点 + role_menu 绑定,避免种子回退或主键再撞。
 */
class NotificationWorkflowButtonSeedMigrationTest {

    private static final long ERP_ADMIN_ROLE_ID = 3002L;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:notif_wf_button_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                NotificationWorkflowButtonSeedMigrationTest.class,
                "notif-wf-button-seed-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void notificationManageButtonSeededUnderNotificationCenter() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_code = 'SYSTEM_NOTIFICATION_MANAGE'
                  and parent_id = 5063
                  and menu_type = 'BUTTON'
                  and permission = 'system:notification:manage'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void workflowApproveAndRejectButtonsSeededUnderApprovalTask() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'BUTTON'
                  and parent_id = 5011
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and permission in ('workflow:approve', 'workflow:reject')
                """, Long.class);
        Assertions.assertThat(count).isEqualTo(2L);
    }

    @Test
    void allThreeButtonsBoundToErpAdmin() {
        Long boundCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.permission in ('system:notification:manage', 'workflow:approve', 'workflow:reject')
                """, Long.class, ERP_ADMIN_ROLE_ID);
        Assertions.assertThat(boundCount).isEqualTo(3L);
    }

}
