package com.tuowei.erp.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NewProdEnvScriptBehaviorTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void requirePowerShellRuntime() {
        String command = resolvePowerShellCommand();
        try {
            Process process = new ProcessBuilder(command, "-NoProfile", "-Command", "exit 0")
                    .redirectErrorStream(true)
                    .start();
            assumeTrue(process.waitFor() == 0, () -> "PowerShell runtime is unavailable: " + command);
        }
        catch (IOException ex) {
            assumeTrue(false, () -> "PowerShell runtime is unavailable: " + command);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            assumeTrue(false, "Interrupted while checking the PowerShell runtime");
        }
    }

    @Test
    void generatorReplacesEveryPlaceholderAndKeepsApplicationDatabasePasswordsAligned() throws Exception {
        Path output = tempDir.resolve(".env.prod");
        ProcessResult result = runPowerShellScript(
                "scripts/new-prod-env.ps1",
                "-TemplatePath", ".env.prod.example",
                "-OutputPath", output.toString(),
                "-CorsAllowedOrigins", "https://preprod.erp.example.com"
        );

        assertThat(result.exitCode())
                .withFailMessage(result.combinedOutput())
                .isZero();

        String generated = Files.readString(output, StandardCharsets.UTF_8);
        Map<String, String> values = readEnvironmentValues(generated);

        assertThat(generated).doesNotContain("CHANGE_ME");
        assertThat(values.get("MYSQL_PASSWORD"))
                .hasSizeGreaterThanOrEqualTo(32)
                .isEqualTo(values.get("ERP_DATASOURCE_PASSWORD"));
        assertThat(values.get("ERP_CORS_ALLOWED_ORIGINS"))
                .isEqualTo("https://preprod.erp.example.com");
    }

    private static Map<String, String> readEnvironmentValues(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                .forEach(line -> {
                    int separator = line.indexOf('=');
                    values.put(line.substring(0, separator), line.substring(separator + 1));
                });
        return values;
    }

    private static ProcessResult runPowerShellScript(String script, String... args)
            throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(resolvePowerShellCommand());
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script);
        command.addAll(java.util.List.of(args));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output.toString(StandardCharsets.UTF_8));
    }

    private static String resolvePowerShellCommand() {
        String explicit = System.getenv("PWSH");
        return explicit == null || explicit.isBlank() ? "pwsh" : explicit;
    }

    private record ProcessResult(int exitCode, String combinedOutput) {
    }
}
