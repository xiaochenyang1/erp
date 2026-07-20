package com.tuowei.erp.system.attachment.web;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        String businessType,
        Long businessId,
        String businessNo,
        String originalFilename,
        String contentType,
        Long fileSize,
        String checksumSha256,
        LocalDateTime createdTime,
        Long createdBy
) {
}
