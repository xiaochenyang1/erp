package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression guard for preserving an existing exception-ticket sequence rule. */
class ExceptionTicketSeedExistingRuleMigrationTest {

    private static final long EXISTING_RULE_ID = 9201L;
    private static final long EXISTING_CURRENT_VALUE = 17L;

    private static JdbcTemplate activeJdbcTemplate;
    private static JdbcTemplate disabledJdbcTemplate;

    @BeforeAll
    static void migrateSchemasWithExistingRules() throws Exception {
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                ExceptionTicketSeedExistingRuleMigrationTest.class,
                "exception-ticket-existing-rule-migrations");
        activeJdbcTemplate = migrateSchema(migrationDir, "active", "ACTIVE");
        disabledJdbcTemplate = migrateSchema(migrationDir, "disabled", "DISABLED");
    }

    @Test
    void preservesExistingActiveRule() {
        assertExistingRuleUnchanged(activeJdbcTemplate, "ACTIVE");
    }

    @Test
    void preservesExistingDisabledRule() {
        assertExistingRuleUnchanged(disabledJdbcTemplate, "DISABLED");
    }

    private static JdbcTemplate migrateSchema(Path migrationDir, String databaseName, String status)
            throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:exception_ticket_existing_" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        String location = "filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/');
        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .target(MigrationVersion.fromVersion("154"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern,
                     seq_length, current_value, status, created_by, updated_by, version)
                values (?, 1, 1, 'EXCEPTION_TICKET', 'CUSTOM-', 'yyyyMM', 6, ?, ?, 42, 43, 9)
                """, EXISTING_RULE_ID, EXISTING_CURRENT_VALUE, status);

        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .load()
                .migrate();
        return jdbcTemplate;
    }

    private static void assertExistingRuleUnchanged(JdbcTemplate jdbcTemplate, String expectedStatus) {
        Long ruleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_sequence_rule
                where company_id = 1
                  and account_book_id = 1
                  and biz_type = 'EXCEPTION_TICKET'
                """, Long.class);
        assertThat(ruleCount).isEqualTo(1L);

        Map<String, Object> rule = jdbcTemplate.queryForMap("""
                select id, company_id, account_book_id, biz_type, prefix, date_pattern,
                       seq_length, current_value, status, created_by, updated_by, version
                from sys_sequence_rule
                where company_id = 1
                  and account_book_id = 1
                  and biz_type = 'EXCEPTION_TICKET'
                """);
        assertThat(rule)
                .containsEntry("id", EXISTING_RULE_ID)
                .containsEntry("company_id", 1L)
                .containsEntry("account_book_id", 1L)
                .containsEntry("biz_type", "EXCEPTION_TICKET")
                .containsEntry("prefix", "CUSTOM-")
                .containsEntry("date_pattern", "yyyyMM")
                .containsEntry("seq_length", 6)
                .containsEntry("current_value", EXISTING_CURRENT_VALUE)
                .containsEntry("status", expectedStatus)
                .containsEntry("created_by", 42L)
                .containsEntry("updated_by", 43L)
                .containsEntry("version", 9);
    }
}
