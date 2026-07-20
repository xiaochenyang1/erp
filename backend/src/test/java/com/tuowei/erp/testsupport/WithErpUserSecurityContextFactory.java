package com.tuowei.erp.testsupport;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class WithErpUserSecurityContextFactory implements WithSecurityContextFactory<WithErpUser> {

    @Override
    public SecurityContext createSecurityContext(WithErpUser annotation) {
        ErpPrincipal principal = new ErpPrincipal(
                annotation.userId(),
                annotation.companyId(),
                annotation.accountBookId(),
                annotation.deptId(),
                annotation.postId(),
                annotation.username(),
                annotation.realName(),
                "N/A",
                new LinkedHashSet<>(Arrays.asList(annotation.authorities())),
                dataScopeSnapshot(annotation)
        );

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        "N/A",
                        principal.getAuthorities()
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    private DataScopeSnapshot dataScopeSnapshot(WithErpUser annotation) {
        if (annotation.allScope()) {
            return DataScopeSnapshot.all();
        }
        Set<Long> warehouseIds = Arrays.stream(annotation.warehouseIds())
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new DataScopeSnapshot(
                false,
                annotation.deptScoped(),
                annotation.postScoped(),
                annotation.selfScoped(),
                warehouseIds
        );
    }
}
