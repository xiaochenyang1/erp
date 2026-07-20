package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class V51__workflow_withdraw_resubmit extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseDialect dialect = DatabaseDialect.from(connection);

        dropCheckConstraints(connection, dialect, "wf_approval_instance");
        dropCheckConstraints(connection, dialect, "wf_approval_record");

        execute(connection, """
                ALTER TABLE wf_approval_instance
                    ADD CONSTRAINT ck_wf_approval_instance_status
                    CHECK (status IN ('IN_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN'))
                """);
        execute(connection, """
                ALTER TABLE wf_approval_record
                    ADD CONSTRAINT ck_wf_approval_record_action
                    CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL', 'WITHDRAW'))
                """);

        dropIndex(connection, dialect, "wf_approval_instance", "uk_wf_instance_active_source");
        execute(connection, """
                ALTER TABLE wf_approval_instance
                    ADD COLUMN active_status VARCHAR(32)
                        GENERATED ALWAYS AS (
                            CASE WHEN status = 'IN_APPROVAL' THEN 'IN_APPROVAL' ELSE NULL END
                        )
                """);
        execute(connection, """
                CREATE UNIQUE INDEX uk_wf_instance_active_source
                    ON wf_approval_instance (company_id, account_book_id, business_type, business_id, active_status)
                """);
    }

    private void dropCheckConstraints(Connection connection, DatabaseDialect dialect, String tableName) throws SQLException {
        for (String constraintName : findCheckConstraints(connection, dialect, tableName)) {
            String sql = switch (dialect) {
                case MYSQL -> "ALTER TABLE `%s` DROP CHECK `%s`".formatted(tableName, escapeMysqlIdentifier(constraintName));
                case H2 -> "ALTER TABLE %s DROP CONSTRAINT \"%s\"".formatted(tableName, escapeH2Identifier(constraintName));
            };
            execute(connection, sql);
        }
    }

    private List<String> findCheckConstraints(
            Connection connection,
            DatabaseDialect dialect,
            String tableName
    ) throws SQLException {
        String sql = switch (dialect) {
            case MYSQL -> """
                    SELECT constraint_name
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = DATABASE()
                      AND table_name = ?
                      AND constraint_type = 'CHECK'
                    """;
            case H2 -> """
                    SELECT constraint_name
                    FROM information_schema.table_constraints
                    WHERE lower(table_schema) = 'public'
                      AND lower(table_name) = ?
                      AND constraint_type = 'CHECK'
                    """;
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> constraintNames = new ArrayList<>();
                while (resultSet.next()) {
                    constraintNames.add(resultSet.getString("constraint_name"));
                }
                return constraintNames;
            }
        }
    }

    private void dropIndex(
            Connection connection,
            DatabaseDialect dialect,
            String tableName,
            String indexName
    ) throws SQLException {
        String sql = switch (dialect) {
            case MYSQL -> "DROP INDEX `%s` ON `%s`".formatted(escapeMysqlIdentifier(indexName), tableName);
            case H2 -> "DROP INDEX %s".formatted(indexName);
        };
        execute(connection, sql);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String escapeMysqlIdentifier(String identifier) {
        return identifier.replace("`", "``");
    }

    private String escapeH2Identifier(String identifier) {
        return identifier.replace("\"", "\"\"");
    }

    private enum DatabaseDialect {
        H2,
        MYSQL;

        static DatabaseDialect from(Connection connection) throws SQLException {
            String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (productName.contains("h2")) {
                return H2;
            }
            if (productName.contains("mysql")) {
                return MYSQL;
            }
            throw new IllegalStateException("Unsupported database for workflow migration: " + productName);
        }
    }
}
