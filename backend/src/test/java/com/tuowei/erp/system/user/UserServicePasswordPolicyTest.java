package com.tuowei.erp.system.user;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServicePasswordPolicyTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private DeptMapper deptMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SecurityPrincipalCache principalCache;
    @Mock
    private ScopedUserResolver scopedUserResolver;
    @Mock
    private UserPermissionService userPermissionService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userMapper,
                userRoleMapper,
                roleMapper,
                deptMapper,
                postMapper,
                passwordEncoder,
                auditMetadataFactory,
                refreshTokenService,
                principalCache,
                scopedUserResolver,
                userPermissionService
        );
    }

    @Test
    void createRejectsWeakPasswordBeforeQueryingOrInsertingUser() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");

        UserCreateRequest request = new UserCreateRequest(
                "bob",
                "weak",
                null,
                "Bob",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度必须在12到72位之间");

        verifyNoInteractions(
                auditMetadataFactory,
                userMapper,
                userRoleMapper,
                roleMapper,
                deptMapper,
                postMapper,
                passwordEncoder,
                refreshTokenService,
                principalCache,
                scopedUserResolver,
                userPermissionService
        );
    }

    @Test
    void resetPasswordRejectsWeakPasswordBeforeLoadingUser() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(userMapper.selectById(9002L)).thenReturn(activeUser(9002L));
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> userService.resetPassword(9002L, new ResetPasswordRequest("weak")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度必须在12到72位之间");

        verifyNoInteractions(
                auditMetadataFactory,
                userMapper,
                userRoleMapper,
                roleMapper,
                deptMapper,
                postMapper,
                passwordEncoder,
                refreshTokenService,
                principalCache,
                scopedUserResolver,
                userPermissionService
        );
    }

    private static AuditMetadata audit() {
        return new AuditMetadata(1001L, 1L, 1L, LocalDateTime.of(2026, 6, 8, 10, 0));
    }

    private static UserEntity activeUser(Long id) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setCompanyId(1L);
        entity.setDeletedFlag(0);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        return entity;
    }
}
