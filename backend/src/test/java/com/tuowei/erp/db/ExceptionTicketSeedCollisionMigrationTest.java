package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression guard for the exception-ticket sequence seed when id 2037 is occupied. */
class ExceptionTicketSeedCollisionMigrationTest {

    private static final long COLLIDED_ID = 2037L;
    private static final long LEGACY_RULE_CURRENT_VALUE = 7L;

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchemaWithLegacySequenceCollision() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:exception_ticket_seed_collision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                ExceptionTicketSeedCollisionMigrationTest.class,
                "exception-ticket-seed-collision-migrations");
        String location = "filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/');

        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .target(MigrationVersion.fromVersion("154"))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                insert into sys_sequence_rule
                    (id, company_id, account_book_id, biz_type, prefix, date_pattern,
                     seq_length, current_value, status, created_by, updated_by, version)
                values (?, 1, 1, 'LEGACY_CUSTOM', 'LEG-', 'yyyyMM-', 3, ?, 'ACTIVE', 0, 0, 0)
                """, COLLIDED_ID, LEGACY_RULE_CURRENT_VALUE);

        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .load()
                .migrate();
    }

    @Test
    void keepsThePreexistingRuleAtTheCollidedIdUntouched() {
        String bizType = jdbcTemplate.queryForObject(
                "select biz_type from sys_sequence_rule where id = ?", String.class, COLLIDED_ID);
        String prefix = jdbcTemplate.queryForObject(
                "select prefix from sys_sequence_rule where id = ?", String.class, COLLIDED_ID);
        Long currentValue = jdbcTemplate.queryForObject(
                "select current_value from sys_sequence_rule where id = ?", Long.class, COLLIDED_ID);

        assertThat(bizType).isEqualTo("LEGACY_CUSTOM");
        assertThat(prefix).isEqualTo("LEG-");
        assertThat(currentValue).isEqualTo(LEGACY_RULE_CURRENT_VALUE);
    }

    @Test
    void insertsExceptionTicketRuleWithAnotherPrimaryKey() {
        Long ruleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_sequence_rule
                where company_id = 1
                  and account_book_id = 1
                  and biz_type = 'EXCEPTION_TICKET'
                """, Long.class);
        Long ruleId = jdbcTemplate.queryForObject("""
                select id
                from sys_sequence_rule
                where company_id = 1
                  and account_book_id = 1
                  and biz_type = 'EXCEPTION_TICKET'
                """, Long.class);

        assertThat(ruleCount).isEqualTo(1L);
        assertThat(ruleId).isNotEqualTo(COLLIDED_ID);
        assertThat(jdbcTemplate.queryForObject(
                "select prefix from sys_sequence_rule where id = ?", String.class, ruleId))
                .isEqualTo("ET-");
    }

    @Test
    void migrationLeavesExactlyOneExceptionTicketRule() {
        Long ruleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_sequence_rule
                where company_id = 1
                  and account_book_id = 1
                  and biz_type = 'EXCEPTION_TICKET'
                """, Long.class);

        assertThat(ruleCount).isEqualTo(1L);
    }
}
