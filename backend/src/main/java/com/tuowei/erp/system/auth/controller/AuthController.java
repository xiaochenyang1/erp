package com.tuowei.erp.system.auth.controller;

import com.tuowei.erp.common.audit.AuditLog;
import com.tuowei.erp.common.ratelimit.RateLimit;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.web.ChangePasswordRequest;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LogoutRequest;
import com.tuowei.erp.system.auth.web.RefreshTokenRequest;
import com.tuowei.erp.system.auth.web.UpdateProfileRequest;
import com.tuowei.erp.system.auth.web.UpdatePreferencesRequest;
import com.tuowei.erp.system.auth.web.UserInfoResponse;
import com.tuowei.erp.system.menu.web.MenuResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @RateLimit(limit = 5, window = 60)
    @AuditLog(module = "认证", operation = AuditLog.OperationType.LOGIN,
              description = "用户登录: #{#request.username()}", logResult = false)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    @AuditLog(module = "认证", operation = AuditLog.OperationType.LOGOUT, description = "用户登出")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success(null);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    @AuditLog(module = "认证", operation = AuditLog.OperationType.UPDATE, description = "修改密码", logParams = false)
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success(null);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/user-info")
    public ApiResponse<UserInfoResponse> getUserInfo() {
        return ApiResponse.success(authService.getUserInfo());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile")
    @AuditLog(module = "认证", operation = AuditLog.OperationType.UPDATE, description = "更新个人资料")
    public ApiResponse<UserInfoResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/preferences")
    @AuditLog(module = "认证", operation = AuditLog.OperationType.UPDATE, description = "更新显示偏好")
    public ApiResponse<UserInfoResponse> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.success(authService.updatePreferences(request));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/runtime-menu-tree")
    public ApiResponse<List<MenuResponse>> getRuntimeMenuTree() {
        return ApiResponse.success(authService.getRuntimeMenuTree());
    }
}
