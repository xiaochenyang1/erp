package com.tuowei.erp.imports.web;

import java.util.List;
import java.util.Map;

public record ImportRowResponse(
        Integer rowNo,
        boolean valid,
        Map<String, String> raw,
        Map<String, Object> normalized,
        List<ImportRowErrorResponse> errors
) {
}
