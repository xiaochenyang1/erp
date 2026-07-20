package com.tuowei.erp.system.user.controller;

import com.tuowei.erp.common.audit.AuditLog;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.user.service.UserDataScopeService;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserRoleAssignmentResponse;
import com.tuowei.erp.system.user.web.UserResponse;
import com.tuowei.erp.system.user.web.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;
    private final UserDataScopeService userDataScopeService;

    public UserController(UserService userService, UserDataScopeService userDataScopeService) {
        this.userService = userService;
        this.userDataScopeService = userDataScopeService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_CREATE)
    @PostMapping
    @AuditLog(module = "用户管理", operation = AuditLog.OperationType.CREATE,
              description = "创建用户: #{#request.username()}", logParams = false)
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(UserPageQuery query) {
        return ApiResponse.success(userService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_UPDATE)
    @PutMapping("/{id}")
    @AuditLog(module = "用户管理", operation = AuditLog.OperationType.UPDATE,
              description = "更新用户ID: #{#id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<UserResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(userService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<UserResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(userService.disable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_RESET_PASSWORD)
    @PostMapping("/{id}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(userService.resetPassword(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ASSIGN_ROLE)
    @PutMapping("/{id}/roles")
    public ApiResponse<UserRoleAssignmentResponse> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignRequest request
    ) {
        return ApiResponse.success(userService.assignRoles(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_VIEW)
    @GetMapping("/{id}/roles")
    public ApiResponse<UserRoleAssignmentResponse> assignedRoles(@PathVariable Long id) {
        return ApiResponse.success(userService.getAssignedRoles(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_VIEW)
    @GetMapping("/{id}/data-scope")
    public ApiResponse<UserDataScopeResponse> assignedDataScope(@PathVariable Long id) {
        return ApiResponse.success(userDataScopeService.getAssigned(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ASSIGN_DATA_SCOPE)
    @PutMapping("/{id}/data-scope")
    @AuditLog(module = "用户管理", operation = AuditLog.OperationType.UPDATE,
              description = "配置用户数据范围: #{#id}")
    public ApiResponse<UserDataScopeResponse> assignDataScope(
            @PathVariable Long id,
            @Valid @RequestBody UserDataScopeAssignRequest request
    ) {
        return ApiResponse.success(userDataScopeService.assign(id, request));
    }
}
