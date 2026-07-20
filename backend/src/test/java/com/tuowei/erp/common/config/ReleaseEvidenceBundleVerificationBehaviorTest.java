package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseEvidenceBundleVerificationBehaviorTest {

    private static final Instant FIXED_ARTIFACT_LAST_WRITE_TIME = Instant.parse("2026-06-12T00:00:00Z");
    private static final String FIXED_ARTIFACT_LAST_WRITE_TIME_TEXT = "2026-06-12T00:00:00.0000000Z";

    @TempDir
    Path tempDir;

    @Test
    void verifierRejectsSha256SidecarThatNamesDifferentBundle() throws Exception {
        Path bundlePath = tempDir.resolve("release-evidence-bundle.zip");
        List<BundleFile> files = minimalReadyEvidenceFiles();
        writeBundle(bundlePath, files);

        String bundleHash = sha256(Files.readAllBytes(bundlePath));
        Files.writeString(
                Path.of(bundlePath + ".sha256"),
                bundleHash + "  other-release-evidence-bundle.zip%n".formatted(),
                StandardCharsets.US_ASCII
        );
        writeSummarySidecars(bundlePath, bundleHash, files.size());

        ProcessResult result = runPowerShellScript("scripts/verify-release-evidence-bundle.ps1", "-BundlePath", bundlePath.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.combinedOutput()).contains("SHA-256 file name");
    }

    @Test
    void artifactsIndexVerifierRejectsBundleSha256SidecarThatNamesDifferentBundle() throws Exception {
        Path bundlePath = tempDir.resolve("release-evidence-bundle.zip");
        Files.writeString(bundlePath, "bundle", StandardCharsets.UTF_8);
        String bundleHash = sha256(Files.readAllBytes(bundlePath));

        Path sha256Path = Path.of(bundlePath + ".sha256");
        Files.writeString(sha256Path, bundleHash + "  other-release-evidence-bundle.zip%n".formatted(), StandardCharsets.US_ASCII);
        Path summaryJsonPath = Path.of(bundlePath + ".summary.json");
        Path summaryMarkdownPath = Path.of(bundlePath + ".summary.md");
        Path verificationJsonPath = Path.of(bundlePath + ".verify-report.json");
        Path verificationMarkdownPath = Path.of(bundlePath + ".verify-report.md");
        Path gateJsonPath = tempDir.resolve("preprod-acceptance-gate.json");
        Path gateVerificationJsonPath = tempDir.resolve("preprod-acceptance-gate.verify-report.json");
        Path gateVerificationMarkdownPath = tempDir.resolve("preprod-acceptance-gate.verify-report.md");

        Files.writeString(summaryJsonPath, """
                {
                  "bundleStatus": "READY",
                  "bundleSha256": "%s",
                  "releaseCheck": {
                    "status": "PASSED",
                    "releaseCandidateCommit": "abc1234",
                    "allowDirtyWorktree": "False"
                  }
                }
                """.formatted(bundleHash), StandardCharsets.UTF_8);
        Files.writeString(summaryMarkdownPath, """
                # Release evidence bundle summary

                | Field | Value |
                | --- | --- |
                | Bundle status | READY |
                | Bundle SHA-256 | %s |
                | Release check status | PASSED |
                | Release candidate commit | abc1234 |
                | Release check allow dirty worktree | False |
                """.formatted(bundleHash), StandardCharsets.UTF_8);
        Files.writeString(verificationJsonPath, "{\"status\":\"PASSED\"}%n".formatted(), StandardCharsets.UTF_8);
        Files.writeString(verificationMarkdownPath, """
                # Release evidence bundle verification report

                | Field | Value |
                | --- | --- |
                | Status | PASSED |
                | Bundle path | %s |
                | SHA-256 file | %s |
                """.formatted(bundlePath.toAbsolutePath(), sha256Path.toAbsolutePath()), StandardCharsets.UTF_8);
        Files.writeString(gateJsonPath, "{\"verdict\":\"READY_FOR_APPROVAL\"}%n".formatted(), StandardCharsets.UTF_8);
        Files.writeString(gateVerificationJsonPath, "{\"status\":\"PASSED\"}%n".formatted(), StandardCharsets.UTF_8);
        Files.writeString(gateVerificationMarkdownPath, """
                # Preproduction acceptance gate report verification

                | Field | Value |
                | --- | --- |
                | Status | PASSED |
                """, StandardCharsets.UTF_8);

        List<Artifact> artifacts = List.of(
                artifact("bundle", bundlePath, "READY"),
                artifact("bundleSha256", sha256Path, "PRESENT"),
                artifact("summaryMarkdown", summaryMarkdownPath, "PRESENT"),
                artifact("summaryJson", summaryJsonPath, "PRESENT"),
                artifact("verificationReportJson", verificationJsonPath, "PASSED"),
                artifact("verificationReportMarkdown", verificationMarkdownPath, "PASSED"),
                artifact("preprodAcceptanceGateJson", gateJsonPath, "PRESENT"),
                artifact("preprodAcceptanceGateVerificationJson", gateVerificationJsonPath, "PASSED"),
                artifact("preprodAcceptanceGateVerificationMarkdown", gateVerificationMarkdownPath, "PASSED")
        );
        Path artifactsIndexJsonPath = tempDir.resolve("release-evidence-artifacts-index.json");
        Path artifactsIndexMarkdownPath = tempDir.resolve("release-evidence-artifacts-index.md");
        writeArtifactsIndex(artifactsIndexJsonPath, artifactsIndexMarkdownPath, bundlePath, artifacts);

        ProcessResult result = runPowerShellScript(
                "scripts/verify-release-evidence-artifacts-index.ps1",
                "-ArtifactsIndexPath",
                artifactsIndexJsonPath.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.combinedOutput()).contains("bundleSha256 file name");
    }

    private static List<BundleFile> minimalReadyEvidenceFiles() {
        return List.of(
                new BundleFile("preprod-acceptance-gate.md", "READY_FOR_APPROVAL%n"),
                new BundleFile("preprod-acceptance-gate.json", """
                        {"verdict":"READY_FOR_APPROVAL"}
                        """),
                new BundleFile("preprod-acceptance-gate.verify-report.json", """
                        {"status":"PASSED"}
                        """),
                new BundleFile("preprod-acceptance-gate.verify-report.md", """
                        # Preproduction acceptance gate report verification

                        | Field | Value |
                        | --- | --- |
                        | Status | PASSED |
                        """),
                new BundleFile("readiness-release-decision.md", "DECIDED_GO%n"),
                new BundleFile("release-check/release-check-report.json", """
                        {
                          "status": "PASSED",
                          "releaseCandidateCommit": "abc1234",
                          "allowDirtyWorktree": false
                        }
                        """),
                new BundleFile("release-check/release-check-report.md", "# Release Check Report%n")
        );
    }

    private static void writeBundle(Path bundlePath, List<BundleFile> files) throws IOException {
        Files.createDirectories(bundlePath.getParent());
        Map<String, byte[]> fileBytes = files.stream()
                .collect(Collectors.toMap(BundleFile::relativePath, file -> file.content().getBytes(StandardCharsets.UTF_8)));
        List<String> sourceEntries = new ArrayList<>();
        for (BundleFile file : files) {
            byte[] bytes = fileBytes.get(file.relativePath());
            sourceEntries.add("""
                    {
                      "relativePath": "%s",
                      "length": %d,
                      "sha256": "%s"
                    }
                    """.formatted(file.relativePath(), bytes.length, sha256(bytes)));
        }

        String manifest = """
                {
                  "schemaVersion": 1,
                  "bundleStatus": "READY",
                  "checks": [
                    {"Name": "minimal fixture", "Status": "PASSED", "Detail": "fixture"}
                  ],
                  "sourceFiles": [%s]
                }
                """.formatted(String.join(",", sourceEntries));

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundlePath), StandardCharsets.UTF_8)) {
            for (BundleFile file : files) {
                zip.putNextEntry(new ZipEntry(file.relativePath()));
                zip.write(fileBytes.get(file.relativePath()));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("release-evidence-bundle-manifest.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void writeSummarySidecars(Path bundlePath, String bundleHash, int sourceFileCount) throws IOException {
        String summaryJson = """
                {
                  "schemaVersion": 1,
                  "bundleStatus": "READY",
                  "bundleSha256": "%s",
                  "sourceFileCount": %d,
                  "failedPrerequisiteCheckCount": 0,
                  "releaseCheck": {
                    "status": "PASSED",
                    "releaseCandidateCommit": "abc1234",
                    "allowDirtyWorktree": "False"
                  }
                }
                """.formatted(bundleHash, sourceFileCount);
        Files.writeString(Path.of(bundlePath + ".summary.json"), summaryJson, StandardCharsets.UTF_8);

        String summaryMarkdown = """
                # Release evidence bundle summary

                | Field | Value |
                | --- | --- |
                | Bundle status | READY |
                | Bundle path | %s |
                | Bundle SHA-256 | %s |
                | SHA-256 file | %s |
                | Source files | %d |
                | Failed prerequisite checks | 0 |
                | Release check status | PASSED |
                | Release candidate commit | abc1234 |
                | Release check allow dirty worktree | False |
                """.formatted(
                bundlePath.toAbsolutePath(),
                bundleHash,
                Path.of(bundlePath + ".sha256").toAbsolutePath(),
                sourceFileCount
        );
        Files.writeString(Path.of(bundlePath + ".summary.md"), summaryMarkdown, StandardCharsets.UTF_8);
    }

    private static ProcessResult runPowerShellScript(String script, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolvePowerShellCommand());
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script);
        command.addAll(List.of(args));

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
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return "pwsh";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).toUpperCase();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static Artifact artifact(String role, Path path, String status) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(FIXED_ARTIFACT_LAST_WRITE_TIME));
        return new Artifact(
                role,
                path.toAbsolutePath().toString(),
                path.getFileName().toString(),
                status,
                Files.size(path),
                sha256(Files.readAllBytes(path)),
                FIXED_ARTIFACT_LAST_WRITE_TIME_TEXT
        );
    }

    private void writeArtifactsIndex(
            Path indexJsonPath,
            Path indexMarkdownPath,
            Path bundlePath,
            List<Artifact> artifacts
    ) throws IOException {
        String generatedAt = FIXED_ARTIFACT_LAST_WRITE_TIME_TEXT;
        String artifactsJson = artifacts.stream()
                .map(Artifact::toJson)
                .collect(Collectors.joining(",%n".formatted()));
        String indexJson = """
                {
                  "schemaVersion": 1,
                  "generatedAt": "%s",
                  "evidenceDirectory": "%s",
                  "bundlePath": "%s",
                  "bundleStatus": "READY",
                  "releaseCheck": {
                    "status": "PASSED",
                    "releaseCandidateCommit": "abc1234",
                    "allowDirtyWorktree": "False"
                  },
                  "verificationStatus": "PASSED",
                  "artifactCount": %d,
                  "missingArtifactCount": 0,
                  "artifacts": [
                %s
                  ]
                }
                """.formatted(
                generatedAt,
                jsonString(tempDir.toAbsolutePath().toString()),
                jsonString(bundlePath.toAbsolutePath().toString()),
                artifacts.size(),
                artifactsJson
        );
        Files.writeString(indexJsonPath, indexJson, StandardCharsets.UTF_8);

        Map<String, String> summaryRows = new LinkedHashMap<>();
        summaryRows.put("Bundle status", "READY");
        summaryRows.put("Verification status", "PASSED");
        summaryRows.put("Release check status", "PASSED");
        summaryRows.put("Release candidate commit", "abc1234");
        summaryRows.put("Release check allow dirty worktree", "False");
        summaryRows.put("Artifact count", String.valueOf(artifacts.size()));
        summaryRows.put("Missing artifacts", "0");
        summaryRows.put("Generated at", generatedAt);

        StringBuilder markdown = new StringBuilder("# Release evidence artifacts index%n%n".formatted());
        markdown.append("| Field | Value |%n".formatted());
        markdown.append("| --- | --- |%n".formatted());
        summaryRows.forEach((field, value) -> markdown.append("| ")
                .append(field)
                .append(" | ")
                .append(markdownValue(value))
                .append(" |%n".formatted()));
        markdown.append("%n".formatted());
        markdown.append("| Artifact | Status | SHA-256 | Bytes | Path |%n".formatted());
        markdown.append("| --- | --- | --- | --- | --- |%n".formatted());
        for (Artifact artifact : artifacts) {
            markdown.append("| ")
                    .append(markdownValue(artifact.role()))
                    .append(" | ")
                    .append(markdownValue(artifact.status()))
                    .append(" | ")
                    .append(markdownValue(artifact.sha256()))
                    .append(" | ")
                    .append(artifact.length())
                    .append(" | ")
                    .append(markdownValue(artifact.path()))
                    .append(" |%n".formatted());
        }
        Files.writeString(indexMarkdownPath, markdown.toString(), StandardCharsets.UTF_8);
    }

    private static String jsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String markdownValue(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private record BundleFile(String relativePath, String content) {
    }

    private record Artifact(
            String role,
            String path,
            String fileName,
            String status,
            long length,
            String sha256,
            String lastWriteTimeUtc
    ) {

        private String toJson() {
            return """
                    {
                      "role": "%s",
                      "path": "%s",
                      "fileName": "%s",
                      "status": "%s",
                      "exists": true,
                      "length": %d,
                      "sha256": "%s",
                      "lastWriteTimeUtc": "%s"
                    }
                    """.formatted(
                    jsonString(role),
                    jsonString(path),
                    jsonString(fileName),
                    jsonString(status),
                    length,
                    jsonString(sha256),
                    jsonString(lastWriteTimeUtc)
            );
        }
    }

    private record ProcessResult(int exitCode, String combinedOutput) {
    }
}
