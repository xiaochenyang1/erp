package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    @Test
    void pomIncludesPrometheusRegistry() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);

        assertThat(pom)
                .contains("<artifactId>micrometer-registry-prometheus</artifactId>");
    }

    @Test
    void productionProfileExposesPrometheusActuatorEndpoint() throws IOException {
        String prodConfig = Files.readString(Path.of("src", "main", "resources", "application-prod.yml"),
                StandardCharsets.UTF_8);

        assertThat(prodConfig)
                .contains("include: health,info,prometheus");
    }

    @Test
    void prometheusEndpointIsNotAnonymousInSecurityConfig() throws IOException {
        String securityConfig = Files.readString(Path.of("src", "main", "java", "com", "tuowei", "erp",
                        "common", "security", "SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(securityConfig)
                .contains("\"/actuator/health\"")
                .doesNotContain("\"/actuator/prometheus\"");
    }
}
