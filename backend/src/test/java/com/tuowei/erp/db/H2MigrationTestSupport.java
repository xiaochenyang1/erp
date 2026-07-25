package com.tuowei.erp.db;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

final class H2MigrationTestSupport {

    private static final String H2_INCOMPATIBLE_MIGRATION = "V77__finance_expense_approval_status.sql";
    private static final String WORKFLOW_SCHEMA_MIGRATION = "V20__workflow_schema.sql";
    private static final String OQC_MIGRATION = "V109__qc_oqc_delivery_columns.sql";
    private static final String WORKFLOW_TIMEOUT_MIGRATION = "V121__workflow_task_timeout_escalation.sql";
    private static final String CUSTOMER_SUPPLIER_PROFILE_MIGRATION = "V123__customer_supplier_profile_fields.sql";

    private H2MigrationTestSupport() {
    }

    static Path copyCompatibleMigrations(Class<?> owner, String directoryPrefix) throws Exception {
        URL migrationUrl = Objects.requireNonNull(
                owner.getClassLoader().getResource("db/migration"),
                "db/migration resource not found");
        Path sourceDir = Path.of(migrationUrl.toURI());
        Path targetDir = Files.createTempDirectory(directoryPrefix);
        try (Stream<Path> migrations = Files.list(sourceDir)) {
            for (Path migration : migrations
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(H2_INCOMPATIBLE_MIGRATION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                Path target = targetDir.resolve(migration.getFileName());
                if (migration.getFileName().toString().equals(WORKFLOW_SCHEMA_MIGRATION)) {
                    Files.writeString(target, h2CompatibleWorkflowSchemaMigration(migration), StandardCharsets.UTF_8);
                } else if (migration.getFileName().toString().equals(OQC_MIGRATION)) {
                    Files.writeString(target, h2CompatibleOqcMigration(migration), StandardCharsets.UTF_8);
                } else if (migration.getFileName().toString().equals(WORKFLOW_TIMEOUT_MIGRATION)) {
                    Files.writeString(target, h2CompatibleWorkflowTimeoutMigration(migration), StandardCharsets.UTF_8);
                } else if (migration.getFileName().toString().equals(CUSTOMER_SUPPLIER_PROFILE_MIGRATION)) {
                    Files.writeString(target, h2CompatibleCustomerSupplierProfileMigration(migration),
                            StandardCharsets.UTF_8);
                } else {
                    Files.copy(migration, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return targetDir;
    }

    private static String h2CompatibleWorkflowSchemaMigration(Path migration) throws Exception {
        String sql = Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return sql.replace(
                "action VARCHAR(32) NOT NULL CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL'))",
                "action VARCHAR(32) NOT NULL CONSTRAINT ck_wf_approval_record_action "
                        + "CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL'))");
    }

    private static String h2CompatibleOqcMigration(Path migration) throws Exception {
        String sql = Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
        sql = sql.replace(
                "ALTER TABLE qc_inspection_order\n"
                        + "    ADD COLUMN inspection_type VARCHAR(16) NOT NULL DEFAULT 'IQC',\n"
                        + "    ADD COLUMN delivery_id BIGINT NULL,\n"
                        + "    MODIFY COLUMN receipt_id BIGINT NULL;",
                "ALTER TABLE qc_inspection_order ADD COLUMN inspection_type VARCHAR(16) NOT NULL DEFAULT 'IQC';\n\n"
                        + "ALTER TABLE qc_inspection_order ADD COLUMN delivery_id BIGINT NULL;\n\n"
                        + "ALTER TABLE qc_inspection_order ALTER COLUMN receipt_id DROP NOT NULL;");
        sql = sql.replace(
                "ALTER TABLE qc_inspection_line\n"
                        + "    ADD COLUMN delivery_line_id BIGINT NULL,\n"
                        + "    MODIFY COLUMN receipt_line_id BIGINT NULL;",
                "ALTER TABLE qc_inspection_line ADD COLUMN delivery_line_id BIGINT NULL;\n\n"
                        + "ALTER TABLE qc_inspection_line ALTER COLUMN receipt_line_id DROP NOT NULL;");
        return sql;
    }

    private static String h2CompatibleWorkflowTimeoutMigration(Path migration) throws Exception {
        String sql = Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
        sql = sql.replace(
                "ALTER TABLE wf_approval_task\n"
                        + "    ADD COLUMN due_time TIMESTAMP NULL,\n"
                        + "    ADD COLUMN escalated_time TIMESTAMP NULL,\n"
                        + "    ADD COLUMN escalation_count INT NOT NULL DEFAULT 0;",
                "ALTER TABLE wf_approval_task ADD COLUMN due_time TIMESTAMP NULL;\n\n"
                        + "ALTER TABLE wf_approval_task ADD COLUMN escalated_time TIMESTAMP NULL;\n\n"
                        + "ALTER TABLE wf_approval_task ADD COLUMN escalation_count INT NOT NULL DEFAULT 0;");
        sql = sql.replace(
                "ALTER TABLE wf_approval_record DROP CHECK ck_wf_approval_record_action;",
                "ALTER TABLE wf_approval_record DROP CONSTRAINT ck_wf_approval_record_action;");
        sql = sql.replace(
                "DATE_ADD(created_time, INTERVAL 24 HOUR)",
                "DATEADD('HOUR', 24, created_time)");
        return sql;
    }

    private static String h2CompatibleCustomerSupplierProfileMigration(Path migration) throws Exception {
        String sql = Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
        sql = sql.replace(
                "ALTER TABLE md_customer\n"
                        + "    ADD COLUMN customer_type VARCHAR(32) NULL,\n"
                        + "    ADD COLUMN email VARCHAR(128) NULL;",
                "ALTER TABLE md_customer ADD COLUMN customer_type VARCHAR(32) NULL;\n\n"
                        + "ALTER TABLE md_customer ADD COLUMN email VARCHAR(128) NULL;");
        sql = sql.replace(
                "ALTER TABLE md_supplier\n"
                        + "    ADD COLUMN email VARCHAR(128) NULL,\n"
                        + "    ADD COLUMN credit_period INT NULL;",
                "ALTER TABLE md_supplier ADD COLUMN email VARCHAR(128) NULL;\n\n"
                        + "ALTER TABLE md_supplier ADD COLUMN credit_period INT NULL;");
        return sql;
    }
}
