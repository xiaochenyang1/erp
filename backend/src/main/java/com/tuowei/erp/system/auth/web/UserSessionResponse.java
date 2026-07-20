package com.tuowei.erp.system.auth.web;

import java.time.LocalDateTime;

public record UserSessionResponse(
        Long id,
        Long userId,
        String username,
        String realName,
        String status,
        String loginIp,
        String userAgent,
        LocalDateTime issuedAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {
}
