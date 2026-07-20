package com.tuowei.erp.imports.web;

import java.util.List;

public record ImportJobResponse(
        Long jobId,
        String importType,
        String fileName,
        String status,
        Integer totalRows,
        Integer validRows,
        Integer errorRows,
        Integer committedRows,
        String errorMessage,
        List<ImportRowResponse> rows
) {
}
