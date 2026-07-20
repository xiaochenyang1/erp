package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadmeDocumentationTest {

    @Test
    void projectEntryPointExplainsRuntimeStackAndLegacyDirectoryName() throws IOException {
        Path readme = Path.of("README.md");

        assertThat(readme)
                .as("Repository entry point must explain how to recognize and run this project")
                .exists()
                .isRegularFile();

        String content = Files.readString(readme, StandardCharsets.UTF_8);
        assertThat(content)
                .contains("Java 17")
                .contains("Spring Boot")
                .contains("Maven")
                .contains("mvnw.cmd")
                .contains("python/erpServer")
                .contains("历史遗留");
    }

    @Test
    void releaseCheckExamplesSeparateReleaseGateFromLocalDirtyWorktreeInvestigation() throws IOException {
        String content = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        assertThat(content)
                .contains("正式发布门禁")
                .contains(".\\scripts\\release-check.ps1 -IncludeTestcontainers")
                .contains("本地非发布排查")
                .contains(".\\scripts\\release-check.ps1 -AllowDirtyWorktree")
                .contains("不要把 `-AllowDirtyWorktree` 用作正式发布证据");

        assertThat(content)
                .doesNotContain("本地发布门禁脚本：\n\n```powershell\n.\\scripts\\release-check.ps1 -AllowDirtyWorktree")
                .doesNotContain("包含 Testcontainers 的发布门禁需要本机可用 Docker：\n\n```powershell\n.\\scripts\\release-check.ps1 -AllowDirtyWorktree -IncludeTestcontainers");
    }

    @Test
    void localLoginDocumentationMatchesLocalProfileBootstrapConfiguration() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        String localConfig = Files.readString(
                Path.of("src", "main", "resources", "application-local.yml"),
                StandardCharsets.UTF_8
        );

        assertThat(readme)
                .contains("## 本地登录")
                .contains("`admin`")
                .contains("`LocalAdmin123`")
                .contains("`ERP_LOCAL_ADMIN_PASSWORD`")
                .contains("`ERP_BOOTSTRAP_ADMIN_PASSWORD`");

        assertThat(localConfig)
                .contains("local-admin-password: ${ERP_LOCAL_ADMIN_PASSWORD:LocalAdmin123}");
    }
}
