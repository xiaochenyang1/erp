package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApiConventionDocumentationTest {

    @Test
    void apiResponseCodeContractIsDocumented() throws IOException {
        Path document = Path.of("docs", "api-conventions.md");

        assertThat(document)
                .as("API response conventions must be documented for frontend and integration clients")
                .exists()
                .isRegularFile();

        String content = Files.readString(document, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("ApiResponse")
                .contains("code")
                .contains("\"0\"")
                .contains("\"401\"")
                .contains("\"403\"")
                .contains("BusinessConflictException")
                .contains("Validation")
                .contains("HTTP");
    }
}
