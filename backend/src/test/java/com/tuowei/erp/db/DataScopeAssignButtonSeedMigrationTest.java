package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 回归守卫:V106 数据范围分配按钮种子必须已落库并绑定 ERP_ADMIN(3002)。
 * 写接口已从 update 码收紧为 assign-data-scope;缺种子会导致 ERP_ADMIN 403。
 */
class DataScopeAssignButtonSeedMigrationTest {

    private static final String H2_INCOMPATIBLE_MIGRATION = "V77__finance_expense_approval_status.sql";
    private static final long ERP_ADMIN_ROLE_ID = 3002L;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:data_scope_assign_button_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = h2CompatibleMigrationDirectory();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void userAssignDataScopeButtonSeededUnderUserMenu() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_code = 'SYSTEM_USER_ASSIGN_DATA_SCOPE'
                  and parent_id = 5002
                  and menu_type = 'BUTTON'
                  and permission = 'system:user:assign-data-scope'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void roleAssignDataScopeButtonSeededUnderRoleMenu() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_menu
                where menu_code = 'SYSTEM_ROLE_ASSIGN_DATA_SCOPE'
                  and parent_id = 5003
                  and menu_type = 'BUTTON'
                  and permission = 'system:role:assign-data-scope'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """, Long.class);
        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void bothButtonsBoundToErpAdmin() {
        Long boundCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_menu rm
                join sys_menu m on m.id = rm.menu_id
                where rm.role_id = ?
                  and m.permission in ('system:user:assign-data-scope', 'system:role:assign-data-scope')
                """, Long.class, ERP_ADMIN_ROLE_ID);
        Assertions.assertThat(boundCount).isEqualTo(2L);
    }

    private static Path h2CompatibleMigrationDirectory() throws Exception {
        URL migrationUrl = Objects.requireNonNull(
                DataScopeAssignButtonSeedMigrationTest.class.getClassLoader().getResource("db/migration"),
                "db/migration resource not found");
        Path sourceDir = Path.of(migrationUrl.toURI());
        Path targetDir = Files.createTempDirectory("data-scope-assign-button-seed-migrations");
        try (Stream<Path> migrations = Files.list(sourceDir)) {
            for (Path migration : migrations
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(H2_INCOMPATIBLE_MIGRATION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                Files.copy(migration, targetDir.resolve(migration.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return targetDir;
    }
}
