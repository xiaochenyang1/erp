package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProfileConfigurationTest {

    @Test
    void testProfileKeepsAutomatedTestOutputQuiet() throws IOException {
        PropertySource<?> testProperties = loadTestProperties();

        assertThat(testProperties.getProperty("spring.main.banner-mode")).isEqualTo("off");
        assertThat(testProperties.getProperty("logging.level.root")).isEqualTo("WARN");
        assertThat(testProperties.getProperty("mybatis-plus.global-config.banner")).isEqualTo(false);
    }

    @Test
    void testProfileCapsHikariPoolSizeForFullSuiteRuns() throws IOException {
        PropertySource<?> testProperties = loadTestProperties();

        assertThat(testProperties.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo(3);
        assertThat(testProperties.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo(0);
        assertThat(testProperties.getProperty("spring.datasource.hikari.idle-timeout")).isEqualTo(10000);
    }

    @Test
    void testProfileUsesDedicatedTestDatabaseByDefault() throws IOException {
        PropertySource<?> testProperties = loadTestProperties();

        assertThat(testProperties.getProperty("spring.datasource.url"))
                .as("Default test profile must not run Flyway against the developer business schema.")
                .isEqualTo("${ERP_TEST_DATASOURCE_URL:jdbc:mysql://localhost:3306/erp_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&createDatabaseIfNotExist=true}");
        assertThat(testProperties.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(testProperties.getProperty("spring.datasource.username"))
                .isEqualTo("${ERP_TEST_DATASOURCE_USERNAME:root}");
        assertThat(testProperties.getProperty("spring.datasource.password"))
                .isEqualTo("${ERP_TEST_DATASOURCE_PASSWORD:12345678}");
    }

    @Test
    void logbackTestSuppressesLogsBeforeSpringContextStarts() throws IOException {
        ClassPathResource logbackTest = new ClassPathResource("logback-test.xml");

        assertThat(logbackTest.exists()).isTrue();

        String content = logbackTest.getContentAsString(StandardCharsets.UTF_8);
        assertThat(content)
                .contains("<root level=\"WARN\">")
                .contains("<appender-ref ref=\"CONSOLE\" />");
    }

    @Test
    void logbackTestSuppressesExpectedGlobalExceptionHandlerStackTraces() throws IOException {
        ClassPathResource logbackTest = new ClassPathResource("logback-test.xml");

        assertThat(logbackTest.exists()).isTrue();

        String content = logbackTest.getContentAsString(StandardCharsets.UTF_8);
        assertThat(content)
                .contains("<logger name=\"com.tuowei.erp.common.exception.GlobalExceptionHandler\" level=\"OFF\" />");
    }

    private PropertySource<?> loadTestProperties() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application-test", new ClassPathResource("application-test.yml"));
        return sources.get(0);
    }
}
