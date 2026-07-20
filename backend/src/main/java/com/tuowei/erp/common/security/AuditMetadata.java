package com.tuowei.erp.common.security;

import java.time.LocalDateTime;

public record AuditMetadata(
        Long userId,
        Long companyId,
        Long accountBookId,
        LocalDateTime now
) {
}
