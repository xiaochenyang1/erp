package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.config.ImportProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CsvImportParser {

    private final ImportProperties properties;

    public CsvImportParser(ImportProperties properties) {
        this.properties = properties == null ? ImportProperties.defaults() : properties;
    }

    public ParsedCsv parse(MultipartFile file, List<String> expectedHeaders) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new IllegalArgumentException(fileSizeLimitMessage());
        }
        List<ParsedLine> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("导入文件没有内容");
        }
        List<String> headers = new ArrayList<>(lines.get(0).values());
        if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
            headers.set(0, headers.get(0).substring(1));
        }
        if (!headers.equals(expectedHeaders)) {
            throw new IllegalArgumentException("CSV表头不匹配，请使用系统提供的模板");
        }
        List<ParsedCsvRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            ParsedLine line = lines.get(i);
            List<String> values = line.values();
            if (values.stream().allMatch(value -> !StringUtils.hasText(value))) {
                continue;
            }
            if (values.size() != headers.size()) {
                throw new IllegalArgumentException("第" + line.rowNo() + "行列数不正确");
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), values.get(j));
            }
            rows.add(new ParsedCsvRow(line.rowNo(), row));
        }
        return new ParsedCsv(headers, rows);
    }

    private List<ParsedLine> readLines(MultipartFile file) {
        try (PushbackReader reader = new PushbackReader(new BufferedReader(
                new InputStreamReader(
                        new SizeLimitingInputStream(file.getInputStream(), properties.maxFileSizeBytes()),
                        StandardCharsets.UTF_8)), 1)) {
            List<ParsedLine> rows = new ArrayList<>();
            List<String> current = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean inQuotes = false;
            boolean quotedCell = false;
            boolean afterClosingQuote = false;
            int rowNo = 1;
            int currentRowNo = 1;
            int ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                if (inQuotes) {
                    if (c == '"') {
                        int next = reader.read();
                        if (next == '"') {
                            appendCellChar(cell, '"', currentRowNo);
                        } else {
                            if (next != -1) {
                                reader.unread(next);
                            }
                            inQuotes = false;
                            afterClosingQuote = true;
                        }
                    } else {
                        if (c == '\n') {
                            rowNo++;
                        }
                        appendCellChar(cell, c, currentRowNo);
                    }
                    continue;
                }
                if (c == '"') {
                    if (cell.length() > 0 || quotedCell || afterClosingQuote) {
                        throw new IllegalArgumentException("第" + rowNo + "行CSV格式错误：引号只能出现在单元格开头");
                    }
                    inQuotes = true;
                    quotedCell = true;
                    continue;
                }
                if (c == ',') {
                    current.add(cell.toString());
                    cell.setLength(0);
                    quotedCell = false;
                    afterClosingQuote = false;
                    continue;
                }
                if (c == '\r') {
                    continue;
                }
                if (c == '\n') {
                    current.add(cell.toString());
                    cell.setLength(0);
                    if (!isBlankRow(current)) {
                        addParsedLine(rows, currentRowNo, current);
                    }
                    current.clear();
                    quotedCell = false;
                    afterClosingQuote = false;
                    rowNo++;
                    currentRowNo = rowNo;
                    continue;
                }
                if (afterClosingQuote && !Character.isWhitespace(c)) {
                    throw new IllegalArgumentException("第" + rowNo + "行CSV格式错误：引号结束后只能跟逗号或换行");
                }
                if (afterClosingQuote) {
                    continue;
                }
                appendCellChar(cell, c, currentRowNo);
            }
            if (inQuotes) {
                throw new IllegalArgumentException("第" + rowNo + "行CSV格式错误：引号未闭合");
            }
            if (!cell.isEmpty() || !current.isEmpty() || quotedCell || afterClosingQuote) {
                current.add(cell.toString());
                if (!isBlankRow(current)) {
                    addParsedLine(rows, currentRowNo, current);
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取CSV文件失败", ex);
        }
    }

    private String fileSizeLimitMessage() {
        return "CSV文件大小不能超过" + properties.maxFileSizeBytes() + "字节";
    }

    private void appendCellChar(StringBuilder cell, char c, int rowNo) {
        if (cell.length() >= properties.maxCellLength()) {
            throw new IllegalArgumentException("第" + rowNo + "行CSV单元格长度不能超过" + properties.maxCellLength() + "字符");
        }
        cell.append(c);
    }

    private void addParsedLine(List<ParsedLine> rows, int rowNo, List<String> current) {
        if (rows.size() >= properties.maxRows() + 1) {
            throw new IllegalArgumentException("CSV数据行不能超过" + properties.maxRows() + "行");
        }
        rows.add(new ParsedLine(rowNo, new ArrayList<>(current)));
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(value -> !StringUtils.hasText(value));
    }

    private final class SizeLimitingInputStream extends FilterInputStream {

        private final long maxBytes;
        private long readBytes;

        private SizeLimitingInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                countBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                countBytes(read);
            }
            return read;
        }

        private void countBytes(int count) {
            readBytes += count;
            if (readBytes > maxBytes) {
                throw new IllegalArgumentException(fileSizeLimitMessage());
            }
        }
    }

    public record ParsedCsv(List<String> headers, List<ParsedCsvRow> rows) {
    }

    public record ParsedCsvRow(int rowNo, Map<String, String> values) {
    }

    private record ParsedLine(int rowNo, List<String> values) {
    }
}
