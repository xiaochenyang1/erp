package com.tuowei.erp.system.auth.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.auth.service.UserSessionService;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.auth.web.UserSessionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserSessionController {

    private final UserSessionService userSessionService;

    public UserSessionController(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_SESSION_VIEW)
    @GetMapping("/api/system/user-sessions")
    public ApiResponse<PageResponse<UserSessionResponse>> list(UserSessionPageQuery query) {
        return ApiResponse.success(userSessionService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_SESSION_REVOKE)
    @PostMapping("/api/system/user-sessions/{id}/revoke")
    public ApiResponse<Void> revokeSession(@PathVariable Long id) {
        userSessionService.revokeSession(id);
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_SESSION_REVOKE)
    @PostMapping("/api/system/users/{id}/sessions/revoke")
    public ApiResponse<Void> revokeUserSessions(@PathVariable Long id) {
        userSessionService.revokeAllForUser(id);
        return ApiResponse.success(null);
    }
}
