package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationHistoryDocumentationTest {

    private static final int LEGACY_MYSQL_VALUES_MAX_VERSION = 145;
    private static final Pattern MIGRATION_VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern DEPRECATED_MYSQL_VALUES_FUNCTION_PATTERN = Pattern.compile(
            "(?i)\\bVALUES\\s*\\(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\)");

    @Test
    void flywayVersionGapsAreDocumented() throws IOException {
        TreeSet<Integer> versions = new TreeSet<>();
        try (var paths = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> MIGRATION_VERSION_PATTERN.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .forEach(versions::add);
        }

        assertThat(versions).isNotEmpty();

        TreeSet<Integer> missingVersions = new TreeSet<>();
        for (int version = versions.first(); version <= versions.last(); version++) {
            if (!versions.contains(version)) {
                missingVersions.add(version);
            }
        }

        if (missingVersions.isEmpty()) {
            return;
        }

        Path historyPath = Path.of("docs", "migrations-history.md");
        assertThat(historyPath)
                .as("Flyway version gaps must be explained for release audits")
                .exists()
                .isRegularFile();

        String history = Files.readString(historyPath, StandardCharsets.UTF_8);
        missingVersions.forEach(version -> assertThat(history)
                .as("docs/migrations-history.md must mention missing V%s", version)
                .contains("V" + version));
    }

    @Test
    void newMigrationsDoNotUseDeprecatedMysqlValuesFunction() throws IOException {
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        try (var paths = Files.list(migrationDirectory)) {
            for (Path path : paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> migrationVersion(path) > LEGACY_MYSQL_VALUES_MAX_VERSION)
                    .toList()) {
                assertThat(Files.readString(path, StandardCharsets.UTF_8))
                        .as("New migration %s must use MySQL row aliases instead of deprecated VALUES(column)",
                                path.getFileName())
                        .doesNotMatch(DEPRECATED_MYSQL_VALUES_FUNCTION_PATTERN);
            }
        }
    }

    private int migrationVersion(Path path) {
        Matcher matcher = MIGRATION_VERSION_PATTERN.matcher(path.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : Integer.MIN_VALUE;
    }
}
