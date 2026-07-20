package com.tuowei.erp.finance.period;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPeriodMenuSeedConfigurationTest {

    @Test
    void accountPeriodMenuSeedUsesFrontendRouteComponentPath() throws Exception {
        Path migrationDir = Path.of("src/main/resources/db/migration");
        String migrations;
        try (var stream = Files.list(migrationDir)) {
            migrations = stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception ex) {
                            throw new IllegalStateException("读取迁移文件失败: " + path, ex);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }

        assertThat(migrations).contains("component = 'finance/periods/index'");
        assertThat(migrations).contains("WHERE menu_code = 'FINANCE_PERIOD'");
    }
}
