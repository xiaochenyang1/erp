package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringDeploymentTemplateTest {
    @Test
    void prometheusTemplateScrapesAuthenticatedActuatorAndRoutesAlerts() throws Exception {
        String config = Files.readString(Path.of("monitoring/prometheus.yml"));
        assertThat(config).contains("job_name: erp-server", "metrics_path: /actuator/prometheus", "basic_auth:");
        assertThat(config).contains("alertmanager:9093", "erp-alert-rules.yml");
    }

    @Test
    void alertmanagerTemplateHasCriticalAndDefaultRoutes() throws Exception {
        String config = Files.readString(Path.of("monitoring/alertmanager.yml"));
        assertThat(config).contains("receiver: erp-default", "severity=\"critical\"", "receiver: erp-critical");
    }
}
