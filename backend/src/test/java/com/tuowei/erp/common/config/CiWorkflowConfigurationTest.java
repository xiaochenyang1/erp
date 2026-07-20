package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CiWorkflowConfigurationTest {

    @Test
    void prVerifyWorkflowRunsMavenTestsOnJava17() throws IOException {
        Path workflow = Path.of(".github", "workflows", "pr-verify.yml");

        assertThat(workflow).exists().isRegularFile();

        String content = Files.readString(workflow, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("pull_request:")
                .contains("push:")
                .contains("actions/checkout@v4")
                .contains("actions/setup-java@v4")
                .contains("distribution: temurin")
                .contains("java-version: '17'")
                .contains("cache: maven")
                .contains("shell: pwsh")
                .contains("./scripts/release-check.ps1 -IncludeTestcontainers")
                .contains("name: Verify release check report")
                .contains("./scripts/verify-release-check-report.ps1 -ReportDirectory target")
                .contains("actions/upload-artifact@v4")
                .contains("if: always()")
                .contains("target/erp-server-1.0.0.jar")
                .contains("target/classes/META-INF/sbom/application.cdx.json")
                .contains("target/bom.json")
                .contains("target/release-check-report.json")
                .contains("target/release-check-report.md")
                .contains("target/surefire-reports/**")
                .contains("if-no-files-found: warn");
        assertThat(content)
                .doesNotContain("verify-release-check-report.ps1 -ReportDirectory target -AllowFailed");

        int releaseCheckStep = content.indexOf("./scripts/release-check.ps1 -IncludeTestcontainers");
        int verifyReportStep = content.indexOf("./scripts/verify-release-check-report.ps1 -ReportDirectory target");
        int uploadArtifactStep = content.indexOf("actions/upload-artifact@v4");

        assertThat(releaseCheckStep).isLessThan(verifyReportStep);
        assertThat(verifyReportStep).isLessThan(uploadArtifactStep);
    }
}
