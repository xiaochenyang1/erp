package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTableCoverageConfigurationTest {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?\\s*\\((.*?)\\);"
    );
    private static final Pattern ALTER_ADD_COMPANY_ID_PATTERN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+`?([a-zA-Z0-9_]+)`?\\s+(?:ADD\\s+(?:COLUMN\\s+)?)`?company_id`?\\b"
    );
    private static final Pattern COMPANY_ID_COLUMN_PATTERN = Pattern.compile("(?i)\\bcompany_id\\b");

    @Test
    void allCompanyIdTablesAreClassifiedAsTenantScopedOrExplicitlyExempted() throws Exception {
        Set<String> companyIdTables = companyIdTablesFromMigrations();
        Set<String> tenantTables = configuredTenantTables();
        Map<String, String> exemptedTables = configuredTenantTableExemptions();

        Set<String> classifiedTables = new TreeSet<>(tenantTables);
        classifiedTables.addAll(exemptedTables.keySet());
        Set<String> missingTables = new TreeSet<>(companyIdTables);
        missingTables.removeAll(classifiedTables);

        Set<String> overlappingTables = new TreeSet<>(tenantTables);
        overlappingTables.retainAll(exemptedTables.keySet());

        assertThat(missingTables)
                .as("company_id tables must be listed in TENANT_TABLES or TENANT_TABLE_EXEMPTIONS")
                .isEmpty();
        assertThat(overlappingTables)
                .as("a table cannot be both tenant-intercepted and explicitly exempted")
                .isEmpty();
        assertThat(exemptedTables)
                .as("tenant interceptor exemptions must document why interceptor injection is unsafe")
                .allSatisfy((table, reason) -> assertThat(reason).isNotBlank());
    }

    private Set<String> companyIdTablesFromMigrations() throws Exception {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(path);
                collectCreateTableCompanyIdColumns(sql, tables);
                collectAlterTableCompanyIdColumns(sql, tables);
            }
        }
        return tables;
    }

    private void collectCreateTableCompanyIdColumns(String sql, Set<String> tables) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            if (COMPANY_ID_COLUMN_PATTERN.matcher(matcher.group(2)).find()) {
                tables.add(normalize(matcher.group(1)));
            }
        }
    }

    private void collectAlterTableCompanyIdColumns(String sql, Set<String> tables) {
        Matcher matcher = ALTER_ADD_COMPANY_ID_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(normalize(matcher.group(1)));
        }
    }

    private Set<String> configuredTenantTables() throws Exception {
        Field field = MybatisPlusConfig.class.getDeclaredField("TENANT_TABLES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> tables = (Set<String>) field.get(null);
        return tables.stream()
                .map(TenantTableCoverageConfigurationTest::normalize)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Map<String, String> configuredTenantTableExemptions() throws Exception {
        Field field;
        try {
            field = MybatisPlusConfig.class.getDeclaredField("TENANT_TABLE_EXEMPTIONS");
        } catch (NoSuchFieldException ignored) {
            return Map.of();
        }
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> exemptions = (Map<String, String>) field.get(null);
        return exemptions.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> normalize(entry.getKey()),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        TreeMap::new
                ));
    }

    private static String normalize(String tableName) {
        return tableName.toLowerCase(java.util.Locale.ROOT);
    }
}
