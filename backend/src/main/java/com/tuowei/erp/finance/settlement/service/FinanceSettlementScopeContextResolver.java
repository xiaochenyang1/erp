package com.tuowei.erp.finance.settlement.service;

import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class FinanceSettlementScopeContextResolver {

    private final CurrentUserContext currentUserContext;
    private final ScopedUserResolver scopedUserResolver;

    public FinanceSettlementScopeContextResolver(
            CurrentUserContext currentUserContext,
            ScopedUserResolver scopedUserResolver
    ) {
        this.currentUserContext = currentUserContext;
        this.scopedUserResolver = scopedUserResolver;
    }

    ResolvedScope resolve() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        return new ResolvedScope(
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    Set<Long> visibleCreatorIds(ResolvedScope scope) {
        Set<Long> creatorIds = new LinkedHashSet<>();
        if (scope.snapshot().selfScoped()) {
            creatorIds.add(scope.currentUser().userId());
        }
        if (scope.snapshot().deptScoped()) {
            creatorIds.addAll(scope.deptUserIds());
        }
        if (scope.snapshot().postScoped()) {
            creatorIds.addAll(scope.postUserIds());
        }
        return creatorIds;
    }

    record ResolvedScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
    }
}
