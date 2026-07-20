package com.tuowei.erp.system.readiness.web;

public record ReadinessItemCreateRequest(
        String itemCode,
        String itemName,
        String category,
        String priority,
        String expectedResult
) {
}
