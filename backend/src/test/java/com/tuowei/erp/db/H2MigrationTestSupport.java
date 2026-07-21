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
    private static final String OQC_MIGRATION = "V109__qc_oqc_delivery_columns.sql";

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
                if (migration.getFileName().toString().equals(OQC_MIGRATION)) {
                    Files.writeString(target, h2CompatibleOqcMigration(migration), StandardCharsets.UTF_8);
                } else {
                    Files.copy(migration, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return targetDir;
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
}
