package com.tuowei.erp.system.notification.web;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long recipientId,
        Long notificationId,
        String category,
        String notificationType,
        String title,
        String content,
        String businessType,
        Long businessId,
        String businessNo,
        String targetUrl,
        boolean readFlag,
        LocalDateTime readTime,
        LocalDateTime createdTime
) {
}
