package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTestScriptConfigurationTest {

    @Test
    void localTestScriptsUseDisposableMysqlAndInjectTestDatasource() throws IOException {
        Path shellScript = Path.of("scripts", "test-local.sh");
        Path powerShellScript = Path.of("scripts", "test-local.ps1");

        assertThat(shellScript).exists().isRegularFile();
        assertThat(powerShellScript).exists().isRegularFile();

        String shell = Files.readString(shellScript, StandardCharsets.UTF_8);
        String powerShell = Files.readString(powerShellScript, StandardCharsets.UTF_8);

        assertThat(shell)
                .contains("docker run --detach --rm")
                .contains("ERP_TEST_DATASOURCE_URL")
                .contains("ERP_TEST_DATASOURCE_USERNAME")
                .contains("ERP_TEST_DATASOURCE_PASSWORD")
                .contains("docker rm -f")
                .contains("DOCKER_HOST")
                .contains("docker context inspect")
                .contains("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
                .contains("goals_present=false")
                .contains("maven_arguments+=(test)")
                .contains("mvnw\" -B \"${maven_arguments[@]}\"");

        assertThat(powerShell)
                .contains("docker run --detach --rm")
                .contains("ERP_TEST_DATASOURCE_URL")
                .contains("ERP_TEST_DATASOURCE_USERNAME")
                .contains("ERP_TEST_DATASOURCE_PASSWORD")
                .contains("docker rm -f")
                .contains("DOCKER_HOST")
                .contains("docker context inspect")
                .contains("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
                .contains("DirectorySeparatorChar")
                .contains("$goalsPresent")
                .contains("$effectiveMavenArguments.Add(\"test\")")
                .contains("& $mavenWrapper -B @effectiveMavenArguments");
    }

    @Test
    void readmeDocumentsTheReproducibleLocalTestEntryPoints() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        assertThat(readme)
                .contains("./scripts/test-local.sh")
                .contains(".\\scripts\\test-local.ps1")
                .contains("一次性 MySQL")
                .contains("ERP_TEST_DATASOURCE_*");
    }
}
