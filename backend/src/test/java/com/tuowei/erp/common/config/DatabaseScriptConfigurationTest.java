package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseScriptConfigurationTest {

    @Test
    void backupPropagatesDumpAndCompressionFailures() throws IOException {
        String script = Files.readString(Path.of("scripts", "backup-database.sh"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("set -euo pipefail")
                .contains("umask 077")
                .contains("if mysqldump")
                .contains("--defaults-extra-file=\"$MYSQL_DEFAULTS_FILE\"")
                .contains("TEMP_BACKUP_FILE")
                .contains("file_size_bytes")
                .contains("拒绝覆盖")
                .contains("sha256sum -c \"$CHECKSUM_FILE\"")
                .contains("DELETED_COUNT=$(find")
                .doesNotContain("--password=\"$DB_PASSWORD\"")
                .contains("DB_NAME=${MYSQL_DATABASE:-erp_server}");
    }

    @Test
    void restorePropagatesImportFailuresAndRequiresCredentials() throws IOException {
        String script = Files.readString(Path.of("scripts", "restore-database.sh"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("set -euo pipefail")
                .contains("umask 077")
                .contains("DB_NAME=${MYSQL_DATABASE:-erp_server}")
                .contains("数据库密码未设置 (MYSQL_PASSWORD)")
                .contains("校验文件不存在")
                .contains("ALLOW_MISSING_CHECKSUM")
                .contains("--defaults-extra-file=\"$MYSQL_DEFAULTS_FILE\"")
                .contains("if gunzip < \"$BACKUP_FILE\" | mysql")
                .doesNotContain("--password=\"$DB_PASSWORD\"")
                .contains("数据库恢复失败");
    }
}
