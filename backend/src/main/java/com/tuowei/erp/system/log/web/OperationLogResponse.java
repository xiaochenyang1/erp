package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public record OperationLogResponse(
        Long id,
        Long userId,
        String username,
        String module,
        String operation,
        String bizNo,
        String result,
        String message,
        String requestMethod,
        String requestUri,
        LocalDateTime operationTime
) {
}
