package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public record LoginLogResponse(
        Long id,
        Long userId,
        String username,
        String result,
        String message,
        String loginIp,
        String userAgent,
        LocalDateTime loginTime
) {
}
