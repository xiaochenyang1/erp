package com.tuowei.erp.system.user.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserResponse;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 21, 14, 30)
    );
    private static final Long USER_ID = 9001L;
    private static final Long DEPT_ID = 1101L;
    private static final Long POST_ID = 1201L;
    private static final Long ROLE_ID = 1301L;

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private PostMapper postMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecurityPrincipalCache principalCache;
    @Mock private ScopedUserResolver scopedUserResolver;
    @Mock private UserPermissionService userPermissionService;
    @Mock private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantAuditedUserHashesPasswordAndClearsOnlyScopedResolverCache() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(postMapper.selectById(POST_ID)).thenReturn(post(AUDIT.accountBookId(), DEPT_ID));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Password12345")).thenReturn("encoded-password");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(USER_ID);
            return 1;
        });
        UserResponse expected = response("ACTIVE");
        when(userQueryService.toResponse(any(UserEntity.class))).thenReturn(expected);

        UserResponse actual = service().create(new UserCreateRequest(
                "alice",
                "Password12345",
                " EMP-1 ",
                "Alice",
                " alice@example.com ",
                " 13800000000 ",
                " avatar.png ",
                DEPT_ID,
                POST_ID,
                "created"
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        UserEntity inserted = captor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getUsername()).isEqualTo("alice");
        assertThat(inserted.getPassword()).isEqualTo("encoded-password");
        assertThat(inserted.getEmployeeNo()).isEqualTo("EMP-1");
        assertThat(inserted.getEmail()).isEqualTo("alice@example.com");
        assertThat(inserted.getMobile()).isEqualTo("13800000000");
        assertThat(inserted.getAvatar()).isEqualTo("avatar.png");
        assertThat(inserted.getDeptId()).isEqualTo(DEPT_ID);
        assertThat(inserted.getPostId()).isEqualTo(POST_ID);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();

        verify(scopedUserResolver).evictAll();
        verifyNoInteractions(principalCache, userPermissionService, refreshTokenService);
    }

    @Test
    void createRejectsWeakPasswordBeforeAnyCollaboratorInteraction() {
        assertThatThrownBy(() -> service().create(new UserCreateRequest(
                "alice", "weak", null, "Alice", null, null, null, null, null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度必须在12到72位之间");

        verifyNoInteractions(
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
                userPermissionService,
                userQueryService
        );
    }

    @Test
    void createRejectsDepartmentOrPostOutsideCurrentAccountBook() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
        verifyNoInteractions(userMapper);

        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(postMapper.selectById(POST_ID)).thenReturn(post(999L, DEPT_ID));
        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在");
        verifyNoInteractions(userMapper);
    }

    @Test
    void updateAuditsUserAndClearsPrincipalAndScopedResolverCaches() {
        UserEntity entity = user();
        when(userQueryService.requireUser(USER_ID)).thenReturn(entity);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.updateById(entity)).thenReturn(1);
        when(userQueryService.toResponse(entity)).thenReturn(response("ACTIVE"));

        service().update(USER_ID, new UserUpdateRequest(
                " EMP-2 ", "Alice Updated", " alice2@example.com ", " 13900000000 ",
                " avatar2.png ", null, null, "updated"
        ));

        assertThat(entity.getEmployeeNo()).isEqualTo("EMP-2");
        assertThat(entity.getRealName()).isEqualTo("Alice Updated");
        assertThat(entity.getEmail()).isEqualTo("alice2@example.com");
        assertThat(entity.getMobile()).isEqualTo("13900000000");
        assertThat(entity.getAvatar()).isEqualTo("avatar2.png");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(principalCache).evictUser(USER_ID);
        verify(scopedUserResolver).evictAll();
        verifyNoInteractions(userPermissionService, refreshTokenService);
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeCacheInvalidationOrMapping() {
        UserEntity entity = user();
        when(userQueryService.requireUser(USER_ID)).thenReturn(entity);
        when(userMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(USER_ID, new UserUpdateRequest(
                null, "Conflict", null, null, null, null, null, null
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("用户已被其他操作修改，请刷新后重试");

        verify(principalCache, never()).evictUser(any());
        verify(scopedUserResolver, never()).evictAll();
        verify(userQueryService, never()).toResponse(entity);
    }

    @Test
    void statusCommandsAuditAndClearPrincipalAndScopedResolverCaches() {
        UserEntity entity = user();
        when(userQueryService.requireUser(USER_ID)).thenReturn(entity);
        when(userMapper.updateById(entity)).thenReturn(1);
        when(userQueryService.toResponse(entity)).thenReturn(response("ACTIVE"));

        service().disable(USER_ID);
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        service().enable(USER_ID);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        verify(principalCache, times(2)).evictUser(USER_ID);
        verify(scopedUserResolver, times(2)).evictAll();
        verify(userPermissionService, never()).evictUserPermissions(any(), any(), any());
    }

    @Test
    void assignRolesDeduplicatesOrderValidatesTenantAuditsRowsAndInvalidatesPermissionCache() {
        when(userQueryService.requireUser(USER_ID)).thenReturn(user());
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, AUDIT.accountBookId()));
        when(roleMapper.selectById(ROLE_ID + 1)).thenReturn(role(ROLE_ID + 1, AUDIT.accountBookId()));

        var response = service().assignRoles(USER_ID,
                new UserRoleAssignRequest(List.of(ROLE_ID, ROLE_ID + 1, ROLE_ID)));

        assertThat(response.roleIds()).containsExactly(ROLE_ID, ROLE_ID + 1);
        verify(userRoleMapper).delete(any());
        ArgumentCaptor<UserRoleEntity> captor = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserRoleEntity::getRoleId)
                .containsExactly(ROLE_ID, ROLE_ID + 1);
        assertThat(captor.getAllValues()).allSatisfy(item -> {
            assertThat(item.getUserId()).isEqualTo(USER_ID);
            assertThat(item.getCreatedBy()).isEqualTo(AUDIT.userId());
            assertThat(item.getCreatedTime()).isEqualTo(AUDIT.now());
        });
        verify(principalCache).evictUser(USER_ID);
        verify(userPermissionService)
                .evictUserPermissions(USER_ID, AUDIT.companyId(), AUDIT.accountBookId());
        verify(scopedUserResolver, never()).evictAll();
    }

    @Test
    void assignRolesRejectsRoleFromDifferentAccountBookBeforeDeletingExistingAssignments() {
        when(userQueryService.requireUser(USER_ID)).thenReturn(user());
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(ROLE_ID, 999L));

        assertThatThrownBy(() -> service().assignRoles(USER_ID, new UserRoleAssignRequest(List.of(ROLE_ID))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");

        verify(userRoleMapper, never()).delete(any());
        verifyNoInteractions(userRoleMapper);
        verifyNoInteractions(principalCache, userPermissionService);
    }

    @Test
    void resetPasswordAppliesPolicyHashesAuditsRevokesSessionsAndEvictsPrincipalOnly() {
        UserEntity entity = user();
        when(userQueryService.requireUser(USER_ID)).thenReturn(entity);
        when(passwordEncoder.encode("NewPassword12345")).thenReturn("new-hash");
        when(userMapper.updateById(entity)).thenReturn(1);
        when(userQueryService.toResponse(entity)).thenReturn(response("ACTIVE"));

        service().resetPassword(USER_ID, new ResetPasswordRequest("NewPassword12345"));

        assertThat(entity.getPassword()).isEqualTo("new-hash");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(refreshTokenService).revokeAllForUser(USER_ID);
        verify(principalCache).evictUser(USER_ID);
        verify(scopedUserResolver, never()).evictAll();
        verify(userPermissionService, never()).evictUserPermissions(any(), any(), any());
    }

    @Test
    void resetPasswordRejectsWeakPasswordBeforeLoadingUser() {
        assertThatThrownBy(() -> service().resetPassword(USER_ID, new ResetPasswordRequest("weak")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度必须在12到72位之间");

        verifyNoInteractions(
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
                userPermissionService,
                userQueryService
        );
    }

    private UserCommandService service() {
        return new UserCommandService(
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
                userPermissionService,
                userQueryService
        );
    }

    private UserCreateRequest createRequest() {
        return new UserCreateRequest(
                "alice", "Password12345", "EMP-1", "Alice", null, null, null, DEPT_ID, POST_ID, null
        );
    }

    private UserEntity user() {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setUsername("alice");
        entity.setPassword("old-hash");
        entity.setEmployeeNo("EMP-1");
        entity.setRealName("Alice");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private DeptEntity dept(Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(DEPT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PostEntity post(Long accountBookId, Long deptId) {
        PostEntity entity = new PostEntity();
        entity.setId(POST_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setDeptId(deptId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private RoleEntity role(Long id, Long accountBookId) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserResponse response(String status) {
        return new UserResponse(
                USER_ID,
                "alice",
                "EMP-1",
                "Alice",
                null,
                null,
                null,
                DEPT_ID,
                POST_ID,
                status,
                null
        );
    }
}
