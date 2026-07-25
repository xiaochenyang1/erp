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
        Path workflow = Path.of("..", ".github", "workflows", "pr-verify.yml");

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
                .contains("working-directory: backend")
                .contains("image: mysql:8.4")
                .contains("ERP_TEST_DATASOURCE_URL:")
                .contains("shell: pwsh")
                .contains("./scripts/release-check.ps1 -IncludeTestcontainers")
                .contains("name: Verify release check report")
                .contains("./scripts/verify-release-check-report.ps1 -ReportDirectory target")
                .contains("actions/upload-artifact@v4")
                .contains("if: always()")
                .contains("backend/target/erp-server-1.0.0.jar")
                .contains("backend/target/classes/META-INF/sbom/application.cdx.json")
                .contains("backend/target/bom.json")
                .contains("backend/target/release-check-report.json")
                .contains("backend/target/release-check-report.md")
                .contains("backend/target/surefire-reports/**")
                .contains("if-no-files-found: warn");
        assertThat(content)
                .doesNotContain("verify-release-check-report.ps1 -ReportDirectory target -AllowFailed");

        int releaseCheckStep = content.indexOf("./scripts/release-check.ps1 -IncludeTestcontainers");
        int verifyReportStep = content.indexOf("./scripts/verify-release-check-report.ps1 -ReportDirectory target");
        int uploadArtifactStep = content.indexOf("actions/upload-artifact@v4");

        assertThat(releaseCheckStep).isLessThan(verifyReportStep);
        assertThat(verifyReportStep).isLessThan(uploadArtifactStep);
    }

    @Test
    void frontendWorkflowIsDiscoverableFromTheMonorepoRoot() throws IOException {
        Path workflow = Path.of("..", ".github", "workflows", "frontend-verify.yml");

        assertThat(workflow).exists().isRegularFile();

        String content = Files.readString(workflow, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("pull_request:")
                .contains("push:")
                .contains("actions/checkout@v4")
                .contains("actions/setup-node@v4")
                .contains("node-version-file: frontend/.nvmrc")
                .contains("cache-dependency-path: frontend/package-lock.json")
                .contains("working-directory: frontend")
                .contains("npm ci")
                .contains("npm run type-check")
                .contains("npm run lint")
                .contains("npm test")
                .contains("npm run check:contracts")
                .contains("npm run build")
                .contains("npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org");

        assertThat(Path.of(".github", "workflows", "pr-verify.yml")).doesNotExist();
        assertThat(Path.of("..", "frontend", ".github", "workflows", "frontend-verify.yml")).doesNotExist();
    }
}
