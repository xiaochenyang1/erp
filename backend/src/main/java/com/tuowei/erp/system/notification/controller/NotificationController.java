package com.tuowei.erp.system.notification.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.notification.web.NotificationBatchReadRequest;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import com.tuowei.erp.system.notification.web.NotificationResponse;
import com.tuowei.erp.system.notification.web.UnreadCountResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_NOTIFICATION_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(NotificationPageQuery query) {
        return ApiResponse.success(notificationService.listMine(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_NOTIFICATION_VIEW)
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.success(new UnreadCountResponse(notificationService.countUnreadMine()));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_NOTIFICATION_MANAGE)
    @PostMapping("/{recipientId}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long recipientId) {
        return ApiResponse.success(notificationService.markRead(recipientId));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_NOTIFICATION_MANAGE)
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_NOTIFICATION_MANAGE)
    @PostMapping("/read-batch")
    public ApiResponse<Map<String, Integer>> markBatchRead(@Valid @RequestBody NotificationBatchReadRequest request) {
        int updated = notificationService.markBatchRead(request.recipientIds());
        return ApiResponse.success(Map.of("updated", updated));
    }
}
