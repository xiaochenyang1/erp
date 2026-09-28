package com.tuowei.erp.system.auth.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.auth.web.UserSessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade delegating user-session listing to the query service and session revocation to the command service. */
@Service
public class UserSessionService {

    private final UserSessionQueryService queryService;
    private final UserSessionCommandService commandService;

    public UserSessionService(UserSessionQueryService queryService, UserSessionCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSessionResponse> list(UserSessionPageQuery query) {
        return queryService.list(query);
    }

    @Transactional
    public void revokeSession(Long id) {
        commandService.revokeSession(id);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        commandService.revokeAllForUser(userId);
    }
}
