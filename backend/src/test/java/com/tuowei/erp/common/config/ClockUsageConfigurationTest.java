package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClockUsageConfigurationTest {

    @Test
    void productionCodeDoesNotReadSystemLocalDateTimeDirectly() throws IOException {
        List<String> directTimeReads;
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            directTimeReads = paths
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(ClockUsageConfigurationTest::directLocalDateTimeNowCalls)
                    .toList();
        }

        assertThat(directTimeReads)
                .as("production code should use injected Clock or audit metadata instead of LocalDateTime.now()")
                .isEmpty();
    }

    private static Stream<String> directLocalDateTimeNowCalls(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(index -> lines.get(index).contains("LocalDateTime.now()"))
                    .mapToObj(index -> path + ":" + (index + 1));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
