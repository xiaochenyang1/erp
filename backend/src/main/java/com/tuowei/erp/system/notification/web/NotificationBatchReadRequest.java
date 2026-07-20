package com.tuowei.erp.system.notification.web;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record NotificationBatchReadRequest(
        @NotEmpty(message = "recipientIds不能为空") List<Long> recipientIds
) {
}
