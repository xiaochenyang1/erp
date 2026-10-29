package com.tuowei.erp.system.role.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for role queries and commands. */
@Service
public class RoleService {

    private final RoleQueryService roleQueryService;
    private final RoleCommandService roleCommandService;

    public RoleService(RoleQueryService roleQueryService, RoleCommandService roleCommandService) {
        this.roleQueryService = roleQueryService;
        this.roleCommandService = roleCommandService;
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        return roleCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> list(RolePageQuery query) {
        return roleQueryService.list(query == null ? new RolePageQuery() : query);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return roleQueryService.getById(id);
    }

    @Transactional
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        return roleCommandService.update(id, request);
    }

    @Transactional
    public RoleResponse enable(Long id) {
        return roleCommandService.enable(id);
    }

    @Transactional
    public RoleResponse disable(Long id) {
        return roleCommandService.disable(id);
    }

    @Transactional
    public RoleMenuAssignmentResponse assignMenus(Long roleId, RoleMenuAssignRequest request) {
        return roleCommandService.assignMenus(roleId, request);
    }

    @Transactional(readOnly = true)
    public RoleMenuAssignmentResponse getAssignedMenus(Long roleId) {
        return roleQueryService.getAssignedMenus(roleId);
    }
}
