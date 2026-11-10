package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringDeploymentTemplateTest {
    @Test
    void prometheusTemplateScrapesAuthenticatedActuatorAndRoutesAlerts() throws Exception {
        String config = Files.readString(Path.of("monitoring/prometheus.yml"));
        assertThat(config)
                .contains(
                        "job_name: erp-server",
                        "metrics_path: /actuator/prometheus",
                        "scheme: http",
                        "targets: [\"erp-server:8080\"]",
                        "bearer_token_file: /etc/prometheus/secrets/erp-metrics-token"
                )
                .doesNotContain("basic_auth:", "erp-metrics-password", "erp-server:8443");
        assertThat(config)
                .contains("alertmanager:9093", "/etc/prometheus/rules/alert-rules.yml")
                .doesNotContain("/etc/prometheus/erp-alert-rules.yml");
    }

    @Test
    void alertmanagerTemplateHasCriticalAndDefaultRoutes() throws Exception {
        String config = Files.readString(Path.of("monitoring/alertmanager.yml"));
        assertThat(config)
                .contains(
                        "receiver: erp-default",
                        "severity=\"critical\"",
                        "receiver: erp-critical",
                        "webhook_configs:",
                        "url_file:",
                        "send_resolved: true",
                        "equal: [service, instance, component]"
                );
    }

    @Test
    void alertRulesOnlyReferenceMetricsExposedByTheApplication() throws Exception {
        String rules = Files.readString(Path.of("monitoring/alert-rules.yml"));

        assertThat(rules)
                .contains(
                        "up{job=\"erp-server\"}",
                        "jvm_memory_used_bytes",
                        "disk_free_bytes",
                        "http_server_requests_seconds_count",
                        "hikaricp_connections_active",
                        "erp_business_health_overall_status",
                        "erp_business_health_check_count"
                )
                .doesNotContain(
                        "erp_health_status",
                        "erp_business_health_status",
                        "erp_rate_limit_rejected_total",
                        "erp_audit_log_errors_total",
                        "erp_slow_query_total",
                        "erp_redis_connection_errors_total",
                        "erp_cache_hits_total",
                        "erp_cache_misses_total"
                );
    }
}
