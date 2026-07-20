package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NativeSqlTenantScopeConfigurationTest {

    private static final Pattern NATIVE_SQL_USAGE = Pattern.compile(
            "\\bJdbcTemplate\\b|\\bNamedParameterJdbcTemplate\\b|@(Select|Update|Insert|Delete)\\b"
    );

    @Test
    void nativeSqlEntryPointsDeclareTenantScopeReview() throws IOException {
        List<String> missingReview;
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            missingReview = paths
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(NativeSqlTenantScopeConfigurationTest::usesNativeSql)
                    .filter(NativeSqlTenantScopeConfigurationTest::doesNotDeclareTenantScopeReview)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(missingReview)
                .as("native SQL entry points must explicitly document their tenant/account-book guard")
                .isEmpty();
    }

    private static boolean usesNativeSql(Path path) {
        return NATIVE_SQL_USAGE.matcher(read(path)).find();
    }

    private static boolean doesNotDeclareTenantScopeReview(Path path) {
        String source = read(path);
        return !source.contains("@NativeSqlTenantScoped(");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
