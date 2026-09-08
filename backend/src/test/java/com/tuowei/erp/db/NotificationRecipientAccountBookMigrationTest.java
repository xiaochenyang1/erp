package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression guard for V154 recipient account-book backfill and relational scope. */
class NotificationRecipientAccountBookMigrationTest {

    private static final long COMPANY_ID = 994001L;
    private static final long ACCOUNT_BOOK_ID = 994002L;
    private static final long NOTIFICATION_ID = 994003L;
    private static final long RECIPIENT_ID = 994004L;

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchemaAndSeedLegacyRecipient() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:notification_recipient_scope;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                NotificationRecipientAccountBookMigrationTest.class,
                "notification-recipient-scope-migrations");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .target("153")
                .load();
        flyway.migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                insert into sys_notification
                    (id, company_id, account_book_id, category, notification_type, title,
                     content, status, deleted_flag, created_by, updated_by, version)
                values (?, ?, ?, 'NOTICE', 'MIGRATION_TEST', 'legacy notification',
                        'legacy recipient backfill', 'ACTIVE', 0, 0, 0, 0)
                """, NOTIFICATION_ID, COMPANY_ID, ACCOUNT_BOOK_ID);
        // V154 must backfill this pre-migration row from its notification parent.
        jdbcTemplate.update("""
                insert into sys_notification_recipient
                    (id, company_id, notification_id, recipient_user_id, read_flag,
                     status, created_by, updated_by, version)
                values (?, ?, ?, ?, 0, 'ACTIVE', 0, 0, 0)
                """, RECIPIENT_ID, COMPANY_ID, NOTIFICATION_ID, RECIPIENT_ID + 1);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
    }

    @Test
    void addsRecipientAccountBookAndBackfillsExistingRows() {
        Integer columnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_name) = 'sys_notification_recipient'
                  and lower(column_name) = 'account_book_id'
                """, Integer.class);
        Long backfilledBook = jdbcTemplate.queryForObject(
                "select account_book_id from sys_notification_recipient where id = ?", Long.class, RECIPIENT_ID);

        assertThat(columnCount).isEqualTo(1);
        assertThat(backfilledBook).isEqualTo(ACCOUNT_BOOK_ID);
    }

    @Test
    void compositeForeignKeyRejectsCrossAccountBookRecipientReference() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sys_notification_recipient
                    (id, company_id, account_book_id, notification_id, recipient_user_id,
                     read_flag, status, created_by, updated_by, version)
                values (?, ?, ?, ?, ?, 0, 'ACTIVE', 0, 0, 0)
                """, RECIPIENT_ID + 10, COMPANY_ID, ACCOUNT_BOOK_ID + 1, NOTIFICATION_ID, RECIPIENT_ID + 2))
                .isInstanceOf(Exception.class);
    }

    @Test
    void accountBookIndexesArePresentForRecipientReads() {
        assertThat(jdbcTemplate.queryForList("""
                select lower(column_name)
                from information_schema.index_columns
                where lower(table_name) = 'sys_notification_recipient'
                  and lower(index_name) = 'idx_sys_notification_recipient_user_book'
                order by ordinal_position
                """, String.class))
                .containsExactly("company_id", "account_book_id", "recipient_user_id", "status", "read_flag", "created_time");
    }
}
