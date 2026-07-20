package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserPermissionService userPermissionService;
    private final DataScopeService dataScopeService;
    private final SecurityPrincipalCache principalCache;

    public DatabaseUserDetailsService(
            UserMapper userMapper,
            UserPermissionService userPermissionService,
            DataScopeService dataScopeService,
            SecurityPrincipalCache principalCache
    ) {
        this.userMapper = userMapper;
        this.userPermissionService = userPermissionService;
        this.dataScopeService = dataScopeService;
        this.principalCache = principalCache;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .eq(UserEntity::getStatus, "ACTIVE")
                .eq(UserEntity::getDeletedFlag, 0));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在或已停用");
        }
        return toPrincipal(user);
    }

    public ErpPrincipal loadPrincipalByUserId(Long userId) {
        return principalCache.get(userId, () -> loadPrincipalFromDatabase(userId));
    }

    private ErpPrincipal loadPrincipalFromDatabase(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDeletedFlag() == null || user.getDeletedFlag() != 0
                || !"ACTIVE".equals(user.getStatus())) {
            throw new UsernameNotFoundException("用户不存在或已停用");
        }
        return toPrincipal(user);
    }

    private ErpPrincipal toPrincipal(UserEntity user) {
        return new ErpPrincipal(
                user.getId(),
                user.getCompanyId(),
                user.getAccountBookId(),
                user.getDeptId(),
                user.getPostId(),
                user.getUsername(),
                user.getRealName(),
                user.getPassword(),
                userPermissionService.loadPermissions(user.getId(), user.getCompanyId(), user.getAccountBookId()),
                dataScopeService.buildSnapshot(user.getId(), user.getCompanyId(), user.getAccountBookId())
        );
    }
}
