package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseDocumentationConfigurationTest {

    @Test
    void releaseDocumentationKeepsTestCountsTiedToLatestCommandOutput() throws IOException {
        String audit = readDoc("production-readiness-audit.md");
        String checklist = readDoc("business-readiness-checklist.md");

        assertThat(audit)
                .doesNotContain("160 个自动化测试通过")
                .doesNotContain("51 个 `*Test.java` 文件")
                .doesNotContain("默认测试集执行 160 个测试方法")
                .contains("以最新命令输出为准")
                .contains("测试数量、失败数");

        assertThat(checklist)
                .doesNotContain("160 个自动化测试通过")
                .contains("以最新命令输出为准")
                .contains("测试数量、失败数");
    }

    @Test
    void productionDeploymentDocumentsOperationalRuntimeLimits() throws IOException {
        String envTemplate = Files.readString(Path.of(".env.prod.example"), StandardCharsets.UTF_8);
        String deployment = readDoc("production-deployment.md");

        assertThat(envTemplate)
                .contains("ERP_REDIS_TIMEOUT=")
                .contains("ERP_ATTACHMENT_MAX_FILE_SIZE_BYTES=")
                .contains("ERP_IDEMPOTENCY_ENABLED=")
                .contains("ERP_IDEMPOTENCY_TTL_SECONDS=")
                .contains("ERP_IDEMPOTENCY_MAX_REPLAY_BODY_BYTES=")
                .contains("ERP_IDEMPOTENCY_MAX_REQUEST_BODY_BYTES=")
                .contains("ERP_IMPORT_MAX_FILE_SIZE_BYTES=")
                .contains("ERP_IMPORT_MAX_ROWS=")
                .contains("ERP_IMPORT_MAX_CELL_LENGTH=")
                .contains("ERP_IMPORT_COMMIT_BATCH_SIZE=")
                .contains("ERP_REPORT_MAX_EXPORT_ROWS=")
                .contains("ERP_REPORT_EXPORT_BATCH_SIZE=")
                .contains("ERP_PRINCIPAL_CACHE_INVALIDATION_MODE=")
                .contains("ERP_JWT_REFRESH_TOKEN_TTL_SECONDS=");

        assertThat(deployment)
                .contains("ERP_REDIS_TIMEOUT")
                .contains("ERP_ATTACHMENT_MAX_FILE_SIZE_BYTES")
                .contains("ERP_IDEMPOTENCY_ENABLED")
                .contains("ERP_IDEMPOTENCY_TTL_SECONDS")
                .contains("ERP_IDEMPOTENCY_MAX_REPLAY_BODY_BYTES")
                .contains("ERP_IDEMPOTENCY_MAX_REQUEST_BODY_BYTES")
                .contains("ERP_IMPORT_MAX_FILE_SIZE_BYTES")
                .contains("ERP_IMPORT_MAX_ROWS")
                .contains("ERP_IMPORT_MAX_CELL_LENGTH")
                .contains("ERP_IMPORT_COMMIT_BATCH_SIZE")
                .contains("ERP_REPORT_MAX_EXPORT_ROWS")
                .contains("ERP_REPORT_EXPORT_BATCH_SIZE")
                .contains("ERP_PRINCIPAL_CACHE_INVALIDATION_MODE")
                .contains("ERP_JWT_REFRESH_TOKEN_TTL_SECONDS");
    }

    @Test
    void releaseDocumentationDocumentsCiReleaseCheckArtifacts() throws IOException {
        String deployment = readDoc("production-deployment.md");
        String checklist = readDoc("business-readiness-checklist.md");

        assertThat(deployment)
                .contains(".github/workflows/pr-verify.yml")
                .contains("CI")
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("actions/upload-artifact")
                .contains("target/surefire-reports")
                .contains("FAILED")
                .contains("环境指纹")
                .contains("PowerShell")
                .contains("Java")
                .contains("Maven")
                .contains("Docker")
                .contains("GitHub Actions")
                .contains("verify-release-check-report.ps1")
                .contains("Verify release check report")
                .contains("自动复验")
                .contains("自复验");

        assertThat(checklist)
                .contains(".github/workflows/pr-verify.yml")
                .contains("CI")
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("actions/upload-artifact")
                .contains("target/surefire-reports")
                .contains("FAILED")
                .contains("环境指纹")
                .contains("PowerShell")
                .contains("Java")
                .contains("Maven")
                .contains("Docker")
                .contains("GitHub Actions")
                .contains("verify-release-check-report.ps1")
                .contains("Verify release check report")
                .contains("自动复验")
                .contains("自复验");
    }

    @Test
    void releaseDocumentationKeepsDirtyWorktreeReportsOutOfReleaseEvidence() throws IOException {
        String deployment = readDoc("production-deployment.md");
        String checklist = readDoc("business-readiness-checklist.md");

        assertThat(deployment)
                .contains("allowDirtyWorktree=true")
                .contains("默认拒绝")
                .contains("PASSED")
                .contains("只有本地非发布排查才允许追加 `-AllowDirtyWorktree`")
                .contains("正式发布复验不得追加 `-AllowDirtyWorktree`");

        assertThat(checklist)
                .contains("allowDirtyWorktree=true")
                .contains("默认拒绝")
                .contains("PASSED")
                .contains("只有本地非发布排查才允许追加 `-AllowDirtyWorktree`")
                .contains("正式发布复验不得追加 `-AllowDirtyWorktree`");
    }

    @Test
    void historicalAuditReportsAreClearlyMarkedAsArchivedSnapshots() throws IOException {
        assertArchivedHistoricalSnapshot(readDoc("GLOBAL_PROJECT_AUDIT.md"));
        assertArchivedHistoricalSnapshot(readDoc("BACKEND_API_DEVELOPMENT_PROGRESS.md"));
    }

    private static String readDoc(String fileName) throws IOException {
        return Files.readString(Path.of("docs", fileName), StandardCharsets.UTF_8);
    }

    private static void assertArchivedHistoricalSnapshot(String content) {
        assertThat(content)
                .contains("归档历史快照")
                .contains("不代表当前状态")
                .contains("以 `docs/WHAT_IS_MISSING.md` 和最新命令输出为准");
    }
}
