package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAdjustmentApprovalMigrationTest {
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void upgradeExistingInventory() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:inventory_approval_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        var directory = H2MigrationTestSupport.copyCompatibleMigrations(InventoryAdjustmentApprovalMigrationTest.class, "inventory-approval-migration");
        String location = "filesystem:" + directory.toAbsolutePath().toString().replace('\\', '/');
        Flyway.configure().dataSource(source).locations(location).target(MigrationVersion.fromVersion("168")).load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.update("""
                insert into inv_adjustment (id, adjustment_no, warehouse_id, adjustment_date, status)
                values (997001, 'HISTORICAL-POSTED', 1, '2026-01-01', 'POSTED'),
                       (997002, 'EXISTING-DRAFT', 1, '2026-01-01', 'DRAFT')
                """);
        jdbc.update("""
                insert into inv_stock_check (id, check_no, warehouse_id, check_date, status, generated_adjustment_id)
                values (997003, 'HISTORICAL-ADJUSTED', 1, '2026-01-01', 'ADJUSTED', 997001),
                       (997004, 'EXISTING-COUNTED', 1, '2026-01-01', 'COUNTED', null)
                """);
        jdbc.update("""
                insert into sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission)
                values (9000000000001, 0, 'BUTTON', 'CUSTOM-KEEP', 'Custom permission', 'custom:keep')
                """);
        jdbc.update("""
                insert into sys_role (id, company_id, account_book_id, role_code, role_name, status, deleted_flag)
                values (997101, 997101, 2, 'ERP_ADMIN', 'Existing configured admin', 'ACTIVE', 0),
                       (997102, 997102, 3, 'ERP_ADMIN', 'Unconfigured admin', 'ACTIVE', 0)
                """);
        jdbc.update("""
                insert into wf_approval_config (id, company_id, account_book_id, business_type, config_name, status, remark)
                values (997201, 997101, 2, 'INVENTORY_ADJUSTMENT', 'Keep custom configuration', 'DISABLED', 'custom')
                """);
        Flyway.configure().dataSource(source).locations(location).load().migrate();
    }

    @Test
    void preservesPostedFactsAndExistingDrafts() {
        assertThat(jdbc.queryForList("select status from inv_adjustment where id in (997001, 997002) order by id", String.class))
                .containsExactly("POSTED", "DRAFT");
        assertThat(jdbc.queryForList("select status from inv_stock_check where id in (997003, 997004) order by id", String.class))
                .containsExactly("ADJUSTED", "COUNTED");
        assertThat(jdbc.queryForObject("select generated_adjustment_id from inv_stock_check where id = 997003", Long.class))
                .isEqualTo(997001L);
    }

    @Test
    void newConstraintsAcceptApprovalAndPendingCountStatesAndRejectUnknownStates() {
        jdbc.update("""
                insert into inv_adjustment (id, adjustment_no, warehouse_id, adjustment_date, status)
                values (997005, 'APPROVAL-STATES', 1, '2026-01-01', 'SUBMITTED')
                """);
        for (String state : new String[]{"APPROVED", "REJECTED"}) {
            assertThat(jdbc.update("update inv_adjustment set status = ? where id = 997005", state)).isEqualTo(1);
        }
        assertThatThrownBy(() -> jdbc.update("update inv_adjustment set status = 'UNKNOWN' where id = 997005"))
                .isInstanceOf(Exception.class);
        jdbc.update("""
                insert into inv_stock_check (id, check_no, warehouse_id, check_date, status)
                values (997006, 'PENDING-COUNT', 1, '2026-01-01', 'ADJUSTMENT_PENDING')
                """);
    }

    @Test
    void seedsPermissionsAboveOccupiedIdsAndBindsByRoleCode() {
        assertThat(jdbc.queryForObject("select permission from sys_menu where id = 9000000000001", String.class))
                .isEqualTo("custom:keep");
        assertThat(jdbc.queryForList("""
                select m.id from sys_menu m join sys_menu p on p.id = m.parent_id
                where m.permission in ('inventory:adjustment:update', 'inventory:adjustment:submit', 'inventory:adjustment:approve')
                  and p.permission = 'inventory:adjustment:view'
                """, Long.class)).hasSize(3).allMatch(id -> id > 9000000000001L);
        for (long roleId : new long[]{3002, 997101, 997102}) {
            assertThat(jdbc.queryForObject("""
                    select count(*) from sys_role_menu rm join sys_menu m on m.id = rm.menu_id
                    where rm.role_id = ? and m.permission in
                    ('inventory:adjustment:update', 'inventory:adjustment:submit', 'inventory:adjustment:approve')
                    """, Integer.class, roleId)).isEqualTo(3);
        }
    }

    @Test
    void keepsCustomConfigurationAndSeedsAnIndependentScopedDefaultForOtherBooks() {
        assertThat(jdbc.queryForMap("select config_name, status, remark from wf_approval_config where id = 997201"))
                .containsEntry("config_name", "Keep custom configuration")
                .containsEntry("status", "DISABLED").containsEntry("remark", "custom");
        assertThat(jdbc.queryForObject("select count(*) from wf_approval_node where config_id = 997201", Integer.class)).isZero();
        assertThat(jdbc.queryForMap("""
                select c.company_id, c.account_book_id, n.approval_mode, a.approver_type, a.approver_id
                from wf_approval_config c join wf_approval_node n on n.config_id = c.id
                join wf_approval_node_approver a on a.node_id = n.id
                where c.company_id = 997102 and c.account_book_id = 3 and c.business_type = 'INVENTORY_ADJUSTMENT'
                """))
                .containsEntry("company_id", 997102L).containsEntry("account_book_id", 3L)
                .containsEntry("approval_mode", "ANY").containsEntry("approver_type", "ROLE")
                .containsEntry("approver_id", 997102L);
    }
}
