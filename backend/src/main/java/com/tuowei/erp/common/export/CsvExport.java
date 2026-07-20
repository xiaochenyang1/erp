package com.tuowei.erp.common.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class CsvExport {

    private static final String UTF8_BOM = "\uFEFF";
    private static final String LINE_SEPARATOR = "\r\n";

    private CsvExport() {
    }

    public static String write(List<String> headers, List<? extends List<?>> rows) {
        StringBuilder builder = new StringBuilder(UTF8_BOM);
        appendRow(builder, headers);
        for (List<?> row : rows) {
            appendRow(builder, row);
        }
        return builder.toString();
    }

    public static void write(OutputStream outputStream, List<String> headers, Iterable<? extends List<?>> rows) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write(UTF8_BOM);
        writeRow(writer, headers);
        for (List<?> row : rows) {
            writeRow(writer, row);
        }
        writer.flush();
    }

    public static void write(OutputStream outputStream, List<String> headers, RowProducer rowProducer) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write(UTF8_BOM);
        writeRow(writer, headers);
        rowProducer.writeRows(row -> writeRow(writer, row));
        writer.flush();
    }

    private static void appendRow(StringBuilder builder, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(Objects.toString(values.get(i), "")));
        }
        builder.append(LINE_SEPARATOR);
    }

    private static void writeRow(Writer writer, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(Objects.toString(values.get(i), "")));
        }
        writer.write(LINE_SEPARATOR);
    }

    private static String escape(String value) {
        String safeValue = neutralizeFormula(value);
        if (!safeValue.contains(",") && !safeValue.contains("\"") && !safeValue.contains("\r") && !safeValue.contains("\n")) {
            return safeValue;
        }
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private static String neutralizeFormula(String value) {
        int formulaStart = firstFormulaSignificantIndex(value);
        if (formulaStart >= value.length()) {
            return value;
        }
        return switch (value.charAt(formulaStart)) {
            case '=', '+', '-', '@' -> "'" + value;
            default -> value;
        };
    }

    private static int firstFormulaSignificantIndex(String value) {
        int index = 0;
        while (index < value.length() && isFormulaPrefixIgnorable(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isFormulaPrefixIgnorable(char value) {
        return Character.isWhitespace(value)
                || Character.isSpaceChar(value)
                || Character.isISOControl(value)
                || Character.getType(value) == Character.FORMAT;
    }

    @FunctionalInterface
    public interface RowProducer {

        void writeRows(RowWriter rowWriter) throws IOException;
    }

    @FunctionalInterface
    public interface RowWriter {

        void write(List<?> row) throws IOException;
    }
}
