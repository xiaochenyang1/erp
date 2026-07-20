package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAcceptanceScriptConfigurationTest {

    @Test
    void preproductionAcceptanceChecksPrometheusAndBusinessHealth() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-acceptance.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health");
    }

    @Test
    void businessSmokeChecksBusinessHealthEndpoint() throws IOException {
        String script = Files.readString(Path.of("scripts", "business-smoke.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("/api/system/observability/business-health");
    }

    @Test
    void productionDocsMentionObservabilityEndpoints() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health")
                .contains("erp_business_health_overall_status")
                .contains("erp_business_health_check_count")
                .contains("docs/monitoring/prometheus-alert-rules.yml");
        assertThat(checklist)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health")
                .contains("erp_business_health_overall_status")
                .contains("erp_business_health_check_count")
                .contains("docs/monitoring/prometheus-alert-rules.yml");
    }
}
