package com.tuowei.erp.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserContext {

    public CurrentUser requireCurrentUser() {
        ErpPrincipal principal = requirePrincipal();
        return new CurrentUser(
                principal.userId(),
                principal.companyId(),
                principal.accountBookId(),
                principal.deptId(),
                principal.postId(),
                principal.username(),
                principal.realName()
        );
    }

    public ErpPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ErpPrincipal principal)) {
            throw new IllegalStateException("当前用户未登录");
        }
        return principal;
    }
}
