package com.tuowei.erp.db;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@Tag("testcontainers")
class MysqlFlywayMigrationSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "erp.testcontainers.enabled", matches = "true")
    void appliesMigrationsOnMysqlDialect() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("erp_codex_test")
                .withUsername("erp")
                .withPassword("erp")) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement statement = connection.createStatement()) {
                assertCount(statement, """
                        select count(*)
                        from information_schema.tables
                        where table_schema = database()
                          and table_name in ('sys_readiness_run', 'sys_readiness_item', 'sys_readiness_evidence')
                        """, 3L);

                assertCount(statement, """
                        select count(*)
                        from sys_menu
                        where permission in ('system:readiness:view', 'system:readiness:manage', 'system:readiness:decide')
                          and status = 'ACTIVE'
                          and deleted_flag = 0
                        """, 3L);

                assertCount(statement, """
                        select count(*)
                        from sys_config
                        where config_code = 'erp.bootstrap.admin-password-initialized'
                          and config_value = 'false'
                        """, 1L);
            }
        }
    }

    private void assertCount(Statement statement, String sql, long expected) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            Assertions.assertThat(resultSet.next()).isTrue();
            Assertions.assertThat(resultSet.getLong(1)).isEqualTo(expected);
        }
    }
}
