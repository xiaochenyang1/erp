package com.tuowei.erp.system.user.service;

import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility facade for user data scopes. Delegates reads to {@link UserDataScopeQueryService}
 * and writes to {@link UserDataScopeCommandService}, preserving each method's transaction semantics.
 */
@Service
public class UserDataScopeService {

    private final UserDataScopeQueryService queryService;
    private final UserDataScopeCommandService commandService;

    public UserDataScopeService(
            UserDataScopeQueryService queryService,
            UserDataScopeCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public UserDataScopeResponse getAssigned(Long userId) {
        return queryService.getAssigned(userId);
    }

    @Transactional
    public UserDataScopeResponse assign(Long userId, UserDataScopeAssignRequest request) {
        return commandService.assign(userId, request);
    }
}
