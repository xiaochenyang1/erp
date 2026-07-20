package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void corsAllowedOriginsRejectsTrailingEmptyOrigin() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("erp.security.cors.allowed-origins", "https://erp.example.com,");

        assertThatThrownBy(() -> securityConfig.corsConfigurationSource(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("erp.security.cors.allowed-origins 包含空配置项");
    }

    @Test
    void corsAllowedOriginsRejectsWildcardWhenCredentialsAreEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("erp.security.cors.allowed-origins", "*");

        assertThatThrownBy(() -> securityConfig.corsConfigurationSource(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("erp.security.cors.allowed-origins 禁止使用 *");
    }

    @Test
    void corsExposesDownloadHeadersForBrowserExports() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("erp.security.cors.allowed-origins", "https://erp.example.com");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource(environment);
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/reports/purchase-orders/export"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getExposedHeaders())
                .contains(HttpHeaders.CONTENT_DISPOSITION);
    }
}
