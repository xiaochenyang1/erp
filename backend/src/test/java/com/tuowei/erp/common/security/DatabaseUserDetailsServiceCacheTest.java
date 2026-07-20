package com.tuowei.erp.common.security;

import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceCacheTest {

    @Test
    void loadPrincipalByUserIdReusesCachedPrincipalUntilEvicted() {
        UserMapper userMapper = mock(UserMapper.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        SecurityPrincipalCache principalCache = new SecurityPrincipalCache(
                Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(
                userMapper,
                permissionService,
                dataScopeService,
                principalCache
        );

        when(userMapper.selectById(7L)).thenReturn(activeUser(7L));
        when(permissionService.loadPermissions(7L, 1L, 1L)).thenReturn(Set.of("system:user:view"));
        when(dataScopeService.buildSnapshot(7L, 1L, 1L)).thenReturn(DataScopeSnapshot.all());

        ErpPrincipal first = service.loadPrincipalByUserId(7L);
        ErpPrincipal second = service.loadPrincipalByUserId(7L);

        assertThat(second).isSameAs(first);
        verify(userMapper, times(1)).selectById(7L);
        verify(permissionService, times(1)).loadPermissions(7L, 1L, 1L);
        verify(dataScopeService, times(1)).buildSnapshot(7L, 1L, 1L);

        principalCache.evictUser(7L);
        ErpPrincipal reloaded = service.loadPrincipalByUserId(7L);

        assertThat(reloaded).isNotSameAs(first);
        verify(userMapper, times(2)).selectById(7L);
        verify(permissionService, times(2)).loadPermissions(7L, 1L, 1L);
        verify(dataScopeService, times(2)).buildSnapshot(7L, 1L, 1L);
    }

    private static UserEntity activeUser(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCompanyId(1L);
        user.setAccountBookId(1L);
        user.setDeptId(11L);
        user.setPostId(12L);
        user.setUsername("cache-user");
        user.setRealName("Cache User");
        user.setPassword("{noop}password");
        user.setStatus("ACTIVE");
        user.setDeletedFlag(0);
        return user;
    }
}
