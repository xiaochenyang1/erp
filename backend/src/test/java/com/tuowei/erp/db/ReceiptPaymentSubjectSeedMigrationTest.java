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

/** Regression guard for preserving occupied ids and existing chart-of-account customizations in V153. */
class ReceiptPaymentSubjectSeedMigrationTest {

    private static final long COLLIDED_PREPAID_ID = 910010L;
    private static final long COLLIDED_ADVANCE_ID = 910011L;
    private static final long EXISTING_PREPAID_ID = 920001L;

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchemaWithCollisionsAndExistingSubject() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:receipt_payment_subject_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                ReceiptPaymentSubjectSeedMigrationTest.class,
                "receipt-payment-subject-seed-migrations");
        String location = "filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/');

        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .target(MigrationVersion.fromVersion("152"))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        insertSubject(COLLIDED_PREPAID_ID, 99L, 99L, "CUSTOM-A", "Custom A", "ASSET", "DEBIT", "ACTIVE", 0);
        insertSubject(COLLIDED_ADVANCE_ID, 99L, 99L, "CUSTOM-B", "Custom B", "LIABILITY", "CREDIT", "ACTIVE", 0);
        insertSubject(EXISTING_PREPAID_ID, 1L, 1L, "1123", "Custom prepaid", "LIABILITY", "CREDIT", "DISABLED", 1);

        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .load()
                .migrate();
    }

    @Test
    void keepsSubjectsAtOccupiedSeedIdsUntouched() {
        assertThat(subject(COLLIDED_PREPAID_ID))
                .containsEntry("company_id", 99L)
                .containsEntry("account_book_id", 99L)
                .containsEntry("subject_code", "CUSTOM-A")
                .containsEntry("subject_name", "Custom A");
        assertThat(subject(COLLIDED_ADVANCE_ID))
                .containsEntry("company_id", 99L)
                .containsEntry("account_book_id", 99L)
                .containsEntry("subject_code", "CUSTOM-B")
                .containsEntry("subject_name", "Custom B");
    }

    @Test
    void preservesExistingPrepaidSubjectAndOnlySeedsTheMissingAdvanceSubject() {
        assertThat(subject(EXISTING_PREPAID_ID))
                .containsEntry("subject_name", "Custom prepaid")
                .containsEntry("subject_type", "LIABILITY")
                .containsEntry("balance_direction", "CREDIT")
                .containsEntry("status", "DISABLED")
                .containsEntry("deleted_flag", 1);

        Long prepaidCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_account_subject
                where company_id = 1 and account_book_id = 1 and subject_code = '1123'
                """, Long.class);
        Map<String, Object> advance = jdbcTemplate.queryForMap("""
                select id, subject_name, subject_type, balance_direction, status, deleted_flag
                from fin_account_subject
                where company_id = 1 and account_book_id = 1 and subject_code = '2203'
                """);

        assertThat(prepaidCount).isEqualTo(1L);
        assertThat(advance)
                .containsEntry("subject_name", "预收账款")
                .containsEntry("subject_type", "LIABILITY")
                .containsEntry("balance_direction", "CREDIT")
                .containsEntry("status", "ACTIVE")
                .containsEntry("deleted_flag", 0);
        assertThat(((Number) advance.get("id")).longValue())
                .isGreaterThan(EXISTING_PREPAID_ID)
                .isNotEqualTo(COLLIDED_PREPAID_ID)
                .isNotEqualTo(COLLIDED_ADVANCE_ID);
    }

    private static void insertSubject(
            long id,
            long companyId,
            long accountBookId,
            String code,
            String name,
            String type,
            String direction,
            String status,
            int deletedFlag
    ) {
        jdbcTemplate.update("""
                insert into fin_account_subject
                    (id, company_id, account_book_id, subject_code, subject_name, parent_id,
                     subject_type, balance_direction, status, deleted_flag, remark,
                     created_by, updated_by, version)
                values (?, ?, ?, ?, ?, null, ?, ?, ?, ?, 'migration regression', 0, 0, 0)
                """, id, companyId, accountBookId, code, name, type, direction, status, deletedFlag);
    }

    private static Map<String, Object> subject(long id) {
        return jdbcTemplate.queryForMap("""
                select company_id, account_book_id, subject_code, subject_name,
                       subject_type, balance_direction, status, deleted_flag
                from fin_account_subject
                where id = ?
                """, id);
    }
}
