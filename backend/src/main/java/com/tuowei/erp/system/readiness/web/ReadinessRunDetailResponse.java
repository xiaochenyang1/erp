package com.tuowei.erp.system.readiness.web;

import java.util.List;

public record ReadinessRunDetailResponse(
        ReadinessRunResponse run,
        List<ReadinessItemResponse> items
) {
}
