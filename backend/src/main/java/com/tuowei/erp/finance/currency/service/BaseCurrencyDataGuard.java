package com.tuowei.erp.finance.currency.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@NativeSqlTenantScoped("Currency-sensitive metadata is discovered globally, but every data probe binds the selected company_id and account_book_id.")
public class BaseCurrencyDataGuard {

    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "md_account_book_currency",
            "md_exchange_rate"
    );

    private final JdbcTemplate jdbcTemplate;

    public BaseCurrencyDataGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireNoCurrencySensitiveData(Long companyId, Long accountBookId) {
        Boolean hasData = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            List<ScopedTable> tables = currencySensitiveTables(connection);
            for (ScopedTable table : tables) {
                if (hasRows(connection, table, companyId, accountBookId)) {
                    return true;
                }
            }
            return false;
        });
        if (Boolean.TRUE.equals(hasData)) {
            throw new BusinessConflictException("账套已有金额或币种业务数据，不能变更本位币；如需更换，请先执行经审计的历史金额转换");
        }
    }

    private List<ScopedTable> currencySensitiveTables(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = "MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())
                ? null
                : currentSchema(connection);
        List<ScopedTable> discovered = new ArrayList<>();
        try (ResultSet result = metadata.getTables(catalog, schema, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                String tableName = result.getString("TABLE_NAME");
                if (tableName != null) {
                    discovered.add(new ScopedTable(tableName, result.getString("TABLE_SCHEM")));
                }
            }
        }

        List<ScopedTable> tables = new ArrayList<>();
        boolean foundVoucherTable = false;
        for (ScopedTable table : discovered) {
            String tableName = table.name();
            if (EXCLUDED_TABLES.contains(tableName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Set<String> columns = columns(metadata, catalog, table.schema(), tableName);
            boolean scoped = columns.contains("company_id") && columns.contains("account_book_id");
            boolean hasMoneyColumns = columns.stream().anyMatch(BaseCurrencyDataGuard::isMoneyColumn);
            if ("fin_voucher".equalsIgnoreCase(tableName) && scoped && hasMoneyColumns) {
                foundVoucherTable = true;
            }
            if (scoped && hasMoneyColumns) {
                tables.add(table);
            }
        }
        if (!foundVoucherTable) {
            throw new IllegalStateException("无法核实账套凭证数据，拒绝变更本位币");
        }
        return tables;
    }

    private Set<String> columns(DatabaseMetaData metadata, String catalog, String schema, String tableName)
            throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getColumns(catalog, schema, tableName, "%")) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                if (name != null) {
                    columns.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private boolean hasRows(Connection connection, ScopedTable table, Long companyId, Long accountBookId)
            throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        quote = quote == null ? "" : quote.trim();
        String tableName = table.schema() == null || table.schema().isBlank()
                ? quote(table.name(), quote)
                : quote(table.schema(), quote) + "." + quote(table.name(), quote);
        String sql = "SELECT 1 FROM " + tableName + " WHERE " + quote("company_id", quote)
                + " = ? AND " + quote("account_book_id", quote) + " = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, companyId);
            statement.setObject(2, accountBookId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String currentSchema(Connection connection) throws SQLException {
        try {
            return connection.getSchema();
        } catch (AbstractMethodError | SQLException ignored) {
            return null;
        }
    }

    private String quote(String identifier, String quote) {
        if (quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private static boolean isMoneyColumn(String name) {
        return name.equals("currency_code") || name.equals("exchange_rate")
                || name.contains("amount") || name.contains("price") || name.contains("cost")
                || name.contains("balance");
    }

    private record ScopedTable(String name, String schema) { }
}
