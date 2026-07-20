package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.config.ImportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvImportParserLimitsTest {

    private final CsvImportParser parser = new CsvImportParser(ImportProperties.defaults());

    @Test
    void rejectsCsvLargerThanDefaultLimit() {
        String content = "code\n" + "A".repeat(5 * 1024 * 1024);

        assertThatThrownBy(() -> parser.parse(csvFile(content), List.of("code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV文件大小不能超过5242880字节");
    }

    @Test
    void rejectsCsvWhenActualStreamExceedsLimitEvenIfMultipartSizeIsUnderReported() throws Exception {
        CsvImportParser smallLimitParser = new CsvImportParser(new ImportProperties(12, 5_000, 4_096, 500));
        String content = "code\n1234567890123\n";
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> smallLimitParser.parse(file, List.of("code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV文件大小不能超过12字节");
    }

    @Test
    void rejectsCsvWithTooManyRows() {
        StringBuilder content = new StringBuilder("code\n");
        for (int i = 0; i < 5_001; i++) {
            content.append("P").append(i).append('\n');
        }

        assertThatThrownBy(() -> parser.parse(csvFile(content.toString()), List.of("code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV数据行不能超过5000行");
    }

    @Test
    void rejectsCsvCellExceedingDefaultLimit() {
        String content = "code\n" + "A".repeat(4_097) + "\n";

        assertThatThrownBy(() -> parser.parse(csvFile(content), List.of("code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("第2行CSV单元格长度不能超过4096字符");
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "limits.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
