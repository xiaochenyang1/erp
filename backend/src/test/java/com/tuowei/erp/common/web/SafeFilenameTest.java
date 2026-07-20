package com.tuowei.erp.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeFilenameTest {

    @Test
    void normalizesPathSeparatorsControlCharactersAndWindowsUnsafeCharacters() {
        String filename = SafeFilename.normalize("..\\..\\unsafe:\r\nname?.csv", "fallback.csv", 255);

        assertThat(filename).isEqualTo("unsafe___name_.csv");
    }

    @Test
    void fallsBackWhenFilenameHasNoUsableName() {
        assertThat(SafeFilename.normalize(null, "fallback.csv", 255)).isEqualTo("fallback.csv");
        assertThat(SafeFilename.normalize("..", "fallback.csv", 255)).isEqualTo("fallback.csv");
        assertThat(SafeFilename.normalize("C:\\temp\\", "fallback.csv", 255)).isEqualTo("fallback.csv");
    }

    @Test
    void sanitizesFallbackBeforeUsingIt() {
        assertThat(SafeFilename.normalize(null, "..\\fallback:\r\nname?.csv", 255))
                .isEqualTo("fallback___name_.csv");
        assertThat(SafeFilename.normalize(null, "..", 255)).isEqualTo("file");
    }

    @Test
    void limitsLengthByKeepingTheTailOfTheSafeName() {
        String filename = SafeFilename.normalize("a".repeat(300) + ".csv", "fallback.csv", 255);

        assertThat(filename)
                .hasSize(255)
                .endsWith(".csv");
    }

    @Test
    void extractsShortSafeExtensionOrFallsBack() {
        assertThat(SafeFilename.extensionOf("report.csv", ".bin", 32)).isEqualTo(".csv");
        assertThat(SafeFilename.extensionOf(null, ".bin", 32)).isEqualTo(".bin");
        assertThat(SafeFilename.extensionOf("report", ".bin", 32)).isEqualTo(".bin");
        assertThat(SafeFilename.extensionOf("report.", ".bin", 32)).isEqualTo(".bin");
        assertThat(SafeFilename.extensionOf("report.c\r\nsv", ".bin", 32)).isEqualTo(".c__sv");
        assertThat(SafeFilename.extensionOf("report." + "x".repeat(40), ".bin", 32)).isEqualTo(".bin");
    }

    @Test
    void rejectsInvalidExtensionLength() {
        assertThatThrownBy(() -> SafeFilename.extensionOf("report.csv", ".bin", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxExtensionLength must be positive");
    }
}
