package com.tuowei.erp.system.role.service;

import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility facade for role data scopes. Delegates reads to {@link RoleDataScopeQueryService}
 * and writes to {@link RoleDataScopeCommandService}, preserving each method's transaction semantics.
 */
@Service
public class RoleDataScopeService {

    private final RoleDataScopeQueryService queryService;
    private final RoleDataScopeCommandService commandService;

    public RoleDataScopeService(
            RoleDataScopeQueryService queryService,
            RoleDataScopeCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public RoleDataScopeResponse getAssigned(Long roleId) {
        return queryService.getAssigned(roleId);
    }

    @Transactional
    public RoleDataScopeResponse assign(Long roleId, RoleDataScopeAssignRequest request) {
        return commandService.assign(roleId, request);
    }
}
