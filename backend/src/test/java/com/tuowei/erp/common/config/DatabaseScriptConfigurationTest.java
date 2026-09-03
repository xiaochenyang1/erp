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
                .contains("set -o pipefail")
                .contains("if mysqldump")
                .contains("rm -f \"$BACKUP_FILE\"")
                .contains("DB_NAME=${MYSQL_DATABASE:-erp_server}");
    }

    @Test
    void restorePropagatesImportFailuresAndRequiresCredentials() throws IOException {
        String script = Files.readString(Path.of("scripts", "restore-database.sh"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("set -o pipefail")
                .contains("DB_NAME=${MYSQL_DATABASE:-erp_server}")
                .contains("数据库密码未设置 (MYSQL_PASSWORD)")
                .contains("if gunzip < \"$BACKUP_FILE\" | mysql")
                .contains("数据库恢复失败");
    }
}
