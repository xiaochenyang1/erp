package com.tuowei.erp.testsupport;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.PermissionCodes;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class TestSecurityContexts {

    private TestSecurityContexts() {
    }

    public static void useAdmin(long userId, String username) {
        useUser(
                userId,
                1L,
                1L,
                1L,
                1L,
                username,
                username,
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
    }

    public static void useUser(
            long userId,
            long companyId,
            long accountBookId,
            long deptId,
            long postId,
            String username,
            String realName,
            Collection<String> authorities,
            DataScopeSnapshot dataScopeSnapshot
    ) {
        ErpPrincipal principal = new ErpPrincipal(
                userId,
                companyId,
                accountBookId,
                deptId,
                postId,
                username,
                realName,
                "N/A",
                new LinkedHashSet<>(authorities),
                dataScopeSnapshot
        );
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        TestSecurityContextHolder.setContext(context);
    }
}
