package com.tuowei.erp.system.role.controller;

import com.tuowei.erp.common.audit.AuditLog;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.role.service.RoleDataScopeService;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
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
@RequestMapping("/api/system/roles")
public class RoleController {

    private final RoleService roleService;
    private final RoleDataScopeService roleDataScopeService;

    public RoleController(RoleService roleService, RoleDataScopeService roleDataScopeService) {
        this.roleService = roleService;
        this.roleDataScopeService = roleDataScopeService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_CREATE)
    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success(roleService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<RoleResponse>> list(RolePageQuery query) {
        return ApiResponse.success(roleService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(roleService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.success(roleService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<RoleResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(roleService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<RoleResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(roleService.disable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ASSIGN_MENU)
    @PutMapping("/{id}/menus")
    public ApiResponse<RoleMenuAssignmentResponse> assignMenus(
            @PathVariable Long id,
            @Valid @RequestBody RoleMenuAssignRequest request
    ) {
        return ApiResponse.success(roleService.assignMenus(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_VIEW)
    @GetMapping("/{id}/menus")
    public ApiResponse<RoleMenuAssignmentResponse> assignedMenus(@PathVariable Long id) {
        return ApiResponse.success(roleService.getAssignedMenus(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_VIEW)
    @GetMapping("/{id}/data-scope")
    public ApiResponse<RoleDataScopeResponse> assignedDataScope(@PathVariable Long id) {
        return ApiResponse.success(roleDataScopeService.getAssigned(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ASSIGN_DATA_SCOPE)
    @PutMapping("/{id}/data-scope")
    @AuditLog(module = "角色管理", operation = AuditLog.OperationType.UPDATE,
              description = "配置角色数据范围: #{#id}")
    public ApiResponse<RoleDataScopeResponse> assignDataScope(
            @PathVariable Long id,
            @Valid @RequestBody RoleDataScopeAssignRequest request
    ) {
        return ApiResponse.success(roleDataScopeService.assign(id, request));
    }
}
