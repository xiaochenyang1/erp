package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

/**
 * 回归守卫:手工凭证相关的两处 sys 主键复用地雷必须已修复。
 * ① sys_sequence_rule:V86 复用 V58 的 id=2013(FIN_BANK_STATEMENT)播种 FIN_MANUAL_VOUCHER,
 *    upsert 不改 biz_type → 规则从未生成、创建必失败;V99 用新主键补种。
 * ② sys_menu:V86 用 5104-5107 播种手工凭证菜单+按钮,V87 复用同 id 播种库存补货并覆盖
 *    path/permission → 手工凭证菜单与 finance:voucher:manage/approve/post 按钮消失;V100 重建。
 */
class ManualVoucherSeedCollisionMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mv_seed_collision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                ManualVoucherSeedCollisionMigrationTest.class,
                "mv-seq-smoke-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void manualVoucherSequenceRuleExistsAfterAllMigrations() {
        Long ruleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_sequence_rule
                where biz_type = 'FIN_MANUAL_VOUCHER'
                  and status = 'ACTIVE'
                """, Long.class);
        Assertions.assertThat(ruleCount).isEqualTo(1L);
    }

    @Test
    void bankStatementRuleStillIntactOnColllidedId() {
        // id=2013 归 FIN_BANK_STATEMENT(V58);手工凭证已迁到独立主键,二者互不覆盖
        String bizType = jdbcTemplate.queryForObject(
                "select biz_type from sys_sequence_rule where id = 2013", String.class);
        Assertions.assertThat(bizType).isEqualTo("FIN_BANK_STATEMENT");
    }

    @Test
    void manualVoucherMenuAndButtonsSurviveMenuIdCollision() {
        // 缺陷:V86 用 sys_menu 5104-5107 播种手工凭证菜单+按钮,V87 复用同一批 id 播种库存补货,
        // 覆盖了 path/permission,致手工凭证菜单与 finance:voucher:manage/approve/post 按钮消失。V100 重建。
        Long manualMenuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_code = 'FINANCE_MANUAL_VOUCHER'
                  and path = '/finance/vouchers/manual'
                  and menu_type = 'MENU'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(manualMenuCount).isEqualTo(1L);

        Long voucherButtonCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_type = 'BUTTON'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                  and permission in ('finance:voucher:manage', 'finance:voucher:approve', 'finance:voucher:post')
                """, Long.class);
        Assertions.assertThat(voucherButtonCount).isEqualTo(3L);
    }

    @Test
    void replenishmentMenuCodesCorrectedAfterCollision() {
        // 被 V87 覆盖后残留的错误 menu_code(FINANCE_VOUCHER_*)已由 V100 纠正为 INVENTORY_REPLENISHMENT_*
        Long mismatched = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where id in (5104, 5105, 5106, 5107)
                  and menu_code like 'FINANCE_%'
                """, Long.class);
        Assertions.assertThat(mismatched).isZero();
    }

}
