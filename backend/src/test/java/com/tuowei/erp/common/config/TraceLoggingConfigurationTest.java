package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TraceLoggingConfigurationTest {

    @Test
    void logPatternIncludesTraceIdFromMdc() throws IOException {
        String logback = Files.readString(Path.of("src", "main", "resources", "logback-spring.xml"),
                StandardCharsets.UTF_8);

        assertThat(logback)
                .contains("%X{traceId:-}");
    }

    @Test
    void mdcTraceFilterRunsBeforeJwtAuthenticationFilter() throws IOException {
        String securityConfig = Files.readString(Path.of("src", "main", "java", "com", "tuowei", "erp",
                        "common", "security", "SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(securityConfig)
                .contains("MdcTraceFilter mdcTraceFilter")
                .contains(".addFilterBefore(mdcTraceFilter, UsernamePasswordAuthenticationFilter.class)")
                .contains(".addFilterAfter(jwtAuthenticationFilter, MdcTraceFilter.class)")
                .contains("MdcTraceFilter.TRACE_ID_HEADER");
    }
}
