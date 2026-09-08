package com.tuowei.erp.report.service;

import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Resolves the immutable user scope used by all business-trace queries. */
@Service
public class BusinessTraceScopeService {
    private final CurrentUserContext currentUserContext;
    private final ScopedUserResolver scopedUserResolver;

    public BusinessTraceScopeService(CurrentUserContext currentUserContext, ScopedUserResolver scopedUserResolver) {
        this.currentUserContext = currentUserContext;
        this.scopedUserResolver = scopedUserResolver;
    }

    public Scope resolve(CurrentUser currentUser) {
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds ids = scopedUserResolver.resolve(currentUser, snapshot);
        return new Scope(currentUser, snapshot, ids.deptUserIds(), ids.postUserIds());
    }

    public record Scope(CurrentUser currentUser, DataScopeSnapshot snapshot,
                        Set<Long> deptUserIds, Set<Long> postUserIds) { }
}
