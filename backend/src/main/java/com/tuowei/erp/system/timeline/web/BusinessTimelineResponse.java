package com.tuowei.erp.system.timeline.web;

import java.time.LocalDateTime;

public record BusinessTimelineResponse(
        Long id,
        String businessType,
        Long businessId,
        String businessNo,
        String eventType,
        String content,
        Long attachmentId,
        Long operatorUserId,
        LocalDateTime createdTime
) {
}
