package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DevProfileConfigurationTest {

    @Test
    void devProfileDoesNotShipDangerousDefaults() throws Exception {
        PropertySource<?> devProperties = loadDevProperties();

        assertThat(devProperties.getProperty("spring.datasource.username"))
                .isEqualTo("${ERP_DATASOURCE_USERNAME}");
        assertThat(devProperties.getProperty("spring.datasource.password"))
                .isEqualTo("${ERP_DATASOURCE_PASSWORD}");
        assertThat(devProperties.getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("${ERP_DEV_API_DOCS_ENABLED:false}");
        assertThat(devProperties.getProperty("springdoc.swagger-ui.enabled"))
                .isEqualTo("${ERP_DEV_SWAGGER_UI_ENABLED:false}");
        assertThat(devProperties.getProperty("erp.error.expose-unexpected-message"))
                .isEqualTo("${ERP_EXPOSE_UNEXPECTED_MESSAGE:false}");
        assertThat(devProperties.getProperty("erp.security.public-api-docs-enabled"))
                .isEqualTo("${ERP_PUBLIC_API_DOCS_ENABLED:false}");
        assertThat(devProperties.getProperty("erp.security.jwt.secret"))
                .isEqualTo("${ERP_JWT_SECRET}");
    }

    private PropertySource<?> loadDevProperties() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application-dev", new ClassPathResource("application-dev.yml"));
        return sources.get(0);
    }
}
