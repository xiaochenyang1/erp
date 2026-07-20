package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PreproductionAcceptanceScriptConfigurationTest {

    @Test
    void preproductionAcceptanceScriptCollectsReleaseAndRuntimeEvidence() throws IOException {
        Path scriptPath = Path.of("scripts", "preprod-acceptance.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EnvFile")
                .contains("[string]$BaseUrl")
                .contains("[string]$OutputPath")
                .contains("[switch]$SkipReleaseCheck")
                .contains("[switch]$SkipComposeUp")
                .contains(".\\scripts\\release-check.ps1 -IncludeTestcontainers")
                .contains("docker --version")
                .contains("docker compose version")
                .contains("docker compose --env-file $EnvFile --profile core up -d --build")
                .contains("docker compose --env-file $EnvFile ps")
                .contains("docker compose --env-file $EnvFile logs --tail 200 erp-server")
                .contains("/actuator/health")
                .contains("/api/health")
                .contains("/api/auth/login")
                .contains("/api/system/profile")
                .contains("ConvertTo-Json")
                .contains("accessToken")
                .contains("Authorization")
                .contains("Preproduction acceptance evidence")
                .contains("Set-Content -LiteralPath $OutputPath");
    }

    @Test
    void preproductionAcceptanceScriptTracksDefaultReadinessItems() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String localRuntime = Files.readString(Path.of("docs", "local-runtime-integration.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Set-TrackedReadinessItemState")
                .contains("function Ensure-TrackedReadinessItemState")
                .contains("function Register-TrackedReadinessEvidence")
                .contains("RELEASE_GATE")
                .contains("DOCKER_COMPOSE_HEALTH")
                .contains("AUTH_SMOKE")
                .contains("PREPROD_ACCEPTANCE")
                .contains("发布门禁未执行")
                .contains("Docker Compose 启动健康检查未执行")
                .contains("登录与受保护接口冒烟未执行")
                .contains("| Item code | Status | Readiness item ID | Readiness evidence ID | Attachment ID |");

        assertThat(deployment)
                .contains("RELEASE_GATE")
                .contains("DOCKER_COMPOSE_HEALTH")
                .contains("AUTH_SMOKE");
        assertThat(localRuntime)
                .contains("RELEASE_GATE")
                .contains("DOCKER_COMPOSE_HEALTH")
                .contains("BLOCKED");
    }

    @Test
    void releaseDocumentsReferencePreproductionAcceptanceScript() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\preprod-acceptance.ps1")
                .contains("预生产验收证据");

        assertThat(checklist)
                .contains(".\\scripts\\preprod-acceptance.ps1")
                .contains("预生产验收证据");
    }
}
