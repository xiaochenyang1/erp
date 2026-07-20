package com.tuowei.erp.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public record ErpPrincipal(
        Long userId,
        Long companyId,
        Long accountBookId,
        Long deptId,
        Long postId,
        String username,
        String realName,
        String password,
        Set<String> permissions,
        DataScopeSnapshot dataScopeSnapshot
) implements UserDetails {

    public ErpPrincipal {
        permissions = Set.copyOf(permissions);
        dataScopeSnapshot = dataScopeSnapshot == null ? DataScopeSnapshot.none() : dataScopeSnapshot;
    }

    public ErpPrincipal(
            Long userId,
            Long companyId,
            Long accountBookId,
            String username,
            String realName,
            String password,
            Set<String> permissions
    ) {
        this(
                userId,
                companyId,
                accountBookId,
                null,
                null,
                username,
                realName,
                password,
                permissions,
                DataScopeSnapshot.none()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
