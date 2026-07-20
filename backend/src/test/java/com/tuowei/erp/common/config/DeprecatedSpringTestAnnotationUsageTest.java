package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeprecatedSpringTestAnnotationUsageTest {

    private static final String DEPRECATED_MOCK_BEAN_IMPORT =
            "org.springframework.boot.test.mock.mockito." + "MockBean";
    private static final String DEPRECATED_MOCK_BEAN_ANNOTATION = "@Mock" + "Bean";

    @Test
    void testsDoNotUseDeprecatedSpringBootMockBeanAnnotation() throws IOException {
        List<Path> offenders;
        try (var paths = Files.walk(Path.of("src", "test", "java"))) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsDeprecatedMockBeanUsage)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    private boolean containsDeprecatedMockBeanUsage(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains(DEPRECATED_MOCK_BEAN_IMPORT)
                    || content.contains(DEPRECATED_MOCK_BEAN_ANNOTATION);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
