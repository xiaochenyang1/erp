package com.tuowei.erp.common.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportStreamingTest {

    @Test
    void writesCsvRowsDirectlyToOutputStream() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CsvExport.write(
                outputStream,
                List.of("name", "amount"),
                List.of(
                        List.of("=SUM(A1:A2)", "10,20"),
                        Arrays.asList(null, "中文")
                )
        );

        assertThat(outputStream.toString(StandardCharsets.UTF_8))
                .isEqualTo("\uFEFFname,amount\r\n'=SUM(A1:A2),\"10,20\"\r\n,中文\r\n");
    }

    @Test
    void neutralizesSpreadsheetFormulasAfterLeadingWhitespace() {
        String csv = CsvExport.write(
                List.of("value"),
                List.of(
                        List.of("+SUM(A1:A2)"),
                        List.of("-10+20"),
                        List.of("@HYPERLINK(\"https://example.test\")"),
                        List.of("  =SUM(A1:A2)"),
                        List.of("\t=SUM(A1:A2)"),
                        List.of("\r=SUM(A1:A2)")
                )
        );

        assertThat(csv)
                .isEqualTo("\uFEFFvalue\r\n"
                        + "'+SUM(A1:A2)\r\n"
                        + "'-10+20\r\n"
                        + "\"'@HYPERLINK(\"\"https://example.test\"\")\"\r\n"
                        + "'  =SUM(A1:A2)\r\n"
                        + "'\t=SUM(A1:A2)\r\n"
                        + "\"'\r=SUM(A1:A2)\"\r\n");
    }
}
