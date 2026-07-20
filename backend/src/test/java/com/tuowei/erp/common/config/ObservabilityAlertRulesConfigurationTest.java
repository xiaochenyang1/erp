package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAlertRulesConfigurationTest {

    @Test
    void prometheusAlertRulesCoverMinimumBusinessHealthSignals() throws IOException {
        String rules = Files.readString(Path.of("docs", "monitoring", "prometheus-alert-rules.yml"),
                StandardCharsets.UTF_8);

        assertThat(rules)
                .contains("ErpReadinessP0P1Unpassed")
                .contains("ErpRecentImportFailures")
                .contains("ErpNegativeInventoryBalance")
                .contains("ErpNoOpenAccountingPeriod")
                .contains("ErpBusinessHealthWarn")
                .contains("erp_business_health_overall_status")
                .contains("erp_business_health_check_count")
                .contains("READINESS_UNPASSED_P0_P1")
                .contains("IMPORT_FAILED_RECENT")
                .contains("NEGATIVE_INVENTORY_BALANCE")
                .contains("OPEN_PERIOD_COUNT");
    }
}
