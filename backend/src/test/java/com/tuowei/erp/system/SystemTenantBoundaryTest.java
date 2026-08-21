package com.tuowei.erp.system;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dept.service.DeptService;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.service.MenuCommandService;
import com.tuowei.erp.system.menu.service.MenuQueryService;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.service.PostService;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SystemTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9901L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 9, 0, 0)
    );

    private static final Long ENTITY_ID = 7101L;
    private static final Long DEPT_ID = 7201L;
    private static final Long POST_ID = 7301L;
    private static final Long ROLE_ID = 7401L;
    private static final Long USER_ID = 7501L;

    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(DeptEntity.class);
        initTableInfo(PostEntity.class);
        initTableInfo(RoleEntity.class);
        initTableInfo(UserEntity.class);
    }

    @Test
    void deptListScopesByCompanyAndAccountBook() {
        stubAudit();
        DeptMapper mapper = mock(DeptMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        deptService(mapper).list(new DeptPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<DeptEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void deptTreeScopesByCompanyAndAccountBook() {
        stubAudit();
        DeptMapper mapper = mock(DeptMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        deptService(mapper).tree();

        ArgumentCaptor<LambdaQueryWrapper<DeptEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void deptDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        DeptMapper mapper = mock(DeptMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(dept(999L));

        assertThatThrownBy(() -> deptService(mapper).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    @Test
    void deptCreateRejectsParentFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        DeptMapper mapper = mock(DeptMapper.class);
        when(mapper.selectById(DEPT_ID)).thenReturn(dept(999L));

        assertThatThrownBy(() -> deptService(mapper).create(new DeptCreateRequest(
                DEPT_ID,
                "D002",
                "账套部门",
                null,
                1,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上级部门不存在");
    }

    @Test
    void postListScopesByCompanyAndAccountBook() {
        stubAudit();
        PostMapper mapper = mock(PostMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        postService(mapper, mock(DeptMapper.class)).list(new PostPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<PostEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void postDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        PostMapper mapper = mock(PostMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(post(999L, DEPT_ID));

        assertThatThrownBy(() -> postService(mapper, mock(DeptMapper.class)).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在");
    }

    @Test
    void postCreateRejectsDepartmentFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        PostMapper postMapper = mock(PostMapper.class);
        DeptMapper deptMapper = mock(DeptMapper.class);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(999L));

        assertThatThrownBy(() -> postService(postMapper, deptMapper).create(new PostCreateRequest(
                DEPT_ID,
                "P002",
                "账套岗位",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    @Test
    void roleListScopesByCompanyAndAccountBook() {
        stubAudit();
        RoleMapper mapper = mock(RoleMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        roleService(mapper).list(new RolePageQuery());

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void roleDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        RoleMapper mapper = mock(RoleMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(role(999L));

        assertThatThrownBy(() -> roleService(mapper).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");
    }

    @Test
    void roleAssignMenusEvictsAccountBookScopedPermissionCache() {
        stubAudit();
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(AUDIT.accountBookId()));
        when(menuMapper.selectById(ENTITY_ID)).thenReturn(menu());

        roleService(roleMapper, menuMapper, roleMenuMapper, permissionService)
                .assignMenus(ROLE_ID, new RoleMenuAssignRequest(List.of(ENTITY_ID)));

        verify(permissionService).evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void roleStatusChangeEvictsAccountBookScopedPermissionCache() {
        stubAudit();
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(AUDIT.accountBookId()));
        when(roleMapper.updateById(any(RoleEntity.class))).thenReturn(1);

        roleService(roleMapper, mock(MenuMapper.class), mock(RoleMenuMapper.class), permissionService)
                .disable(ROLE_ID);

        verify(permissionService).evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void menuUpdateEvictsAccountBookScopedPermissionCache() {
        stubAudit();
        MenuMapper menuMapper = mock(MenuMapper.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        when(menuMapper.selectById(ENTITY_ID)).thenReturn(menu());
        when(menuMapper.updateById(any(MenuEntity.class))).thenReturn(1);

        menuService(menuMapper, permissionService).update(ENTITY_ID, new MenuUpdateRequest(
                "用户管理",
                "/system/users",
                "system/user/index",
                "system:user:view",
                10
        ));

        verify(permissionService).evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void userListScopesByCompanyAndAccountBook() {
        stubAudit();
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        userService(mapper, mock(UserRoleMapper.class), mock(RoleMapper.class), mock(DeptMapper.class), mock(PostMapper.class))
                .list(new UserPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void userDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(user(999L));

        assertThatThrownBy(() -> userService(mapper, mock(UserRoleMapper.class), mock(RoleMapper.class), mock(DeptMapper.class), mock(PostMapper.class))
                .getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void userCreateRejectsDepartmentFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        DeptMapper deptMapper = mock(DeptMapper.class);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(999L));

        assertThatThrownBy(() -> userService(mock(UserMapper.class), mock(UserRoleMapper.class), mock(RoleMapper.class), deptMapper, mock(PostMapper.class))
                .create(userCreateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    @Test
    void userCreateRejectsPostFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        DeptMapper deptMapper = mock(DeptMapper.class);
        PostMapper postMapper = mock(PostMapper.class);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(postMapper.selectById(POST_ID)).thenReturn(post(999L, DEPT_ID));

        assertThatThrownBy(() -> userService(mock(UserMapper.class), mock(UserRoleMapper.class), mock(RoleMapper.class), deptMapper, postMapper)
                .create(userCreateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在");
    }

    @Test
    void userAssignRolesRejectsDifferentAccountBookRoleWithinSameCompany() {
        stubAudit();
        UserMapper userMapper = mock(UserMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        when(userMapper.selectById(USER_ID)).thenReturn(user(AUDIT.accountBookId()));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(999L));

        assertThatThrownBy(() -> userService(userMapper, mock(UserRoleMapper.class), roleMapper, mock(DeptMapper.class), mock(PostMapper.class))
                .assignRoles(USER_ID, new UserRoleAssignRequest(List.of(ROLE_ID))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");
    }

    @Test
    void userAssignRolesEvictsAccountBookScopedPermissionCache() {
        stubAudit();
        UserMapper userMapper = mock(UserMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        when(userMapper.selectById(USER_ID)).thenReturn(user(AUDIT.accountBookId()));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(AUDIT.accountBookId()));

        userService(
                userMapper,
                userRoleMapper,
                roleMapper,
                mock(DeptMapper.class),
                mock(PostMapper.class),
                permissionService
        ).assignRoles(USER_ID, new UserRoleAssignRequest(List.of(ROLE_ID)));

        verify(permissionService).evictUserPermissions(USER_ID, AUDIT.companyId(), AUDIT.accountBookId());
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private DeptService deptService(DeptMapper mapper) {
        return new DeptService(mapper, auditMetadataFactory);
    }

    private PostService postService(PostMapper postMapper, DeptMapper deptMapper) {
        return new PostService(postMapper, deptMapper, auditMetadataFactory);
    }

    private RoleService roleService(RoleMapper roleMapper) {
        return roleService(roleMapper, mock(MenuMapper.class), mock(RoleMenuMapper.class), mock(UserPermissionService.class));
    }

    private RoleService roleService(
            RoleMapper roleMapper,
            MenuMapper menuMapper,
            RoleMenuMapper roleMenuMapper,
            UserPermissionService permissionService
    ) {
        return new RoleService(
                roleMapper,
                menuMapper,
                roleMenuMapper,
                auditMetadataFactory,
                mock(SecurityPrincipalCache.class),
                permissionService
        );
    }

    private MenuService menuService(MenuMapper menuMapper, UserPermissionService permissionService) {
        MenuQueryService queryService = new MenuQueryService(
                menuMapper,
                mock(com.tuowei.erp.common.security.CurrentUserContext.class),
                mock(UserRoleMapper.class),
                mock(RoleMapper.class),
                mock(RoleMenuMapper.class),
                CacheService.NOOP,
                new ObjectMapper()
        );
        MenuCommandService commandService = new MenuCommandService(
                menuMapper,
                auditMetadataFactory,
                mock(SecurityPrincipalCache.class),
                permissionService,
                CacheService.NOOP,
                queryService
        );
        return new MenuService(
                queryService,
                commandService
        );
    }

    private UserService userService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            DeptMapper deptMapper,
            PostMapper postMapper
    ) {
        return userService(userMapper, userRoleMapper, roleMapper, deptMapper, postMapper, mock(UserPermissionService.class));
    }

    private UserService userService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            DeptMapper deptMapper,
            PostMapper postMapper,
            UserPermissionService permissionService
    ) {
        return new UserService(
                userMapper,
                userRoleMapper,
                roleMapper,
                deptMapper,
                postMapper,
                mock(PasswordEncoder.class),
                auditMetadataFactory,
                mock(RefreshTokenService.class),
                mock(SecurityPrincipalCache.class),
                mock(ScopedUserResolver.class),
                permissionService
        );
    }

    private <T> Page<T> page() {
        Page<T> page = new Page<>(1, 20);
        page.setRecords(List.of());
        return page;
    }

    private DeptEntity dept(Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(DEPT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setParentId(0L);
        entity.setDeptCode("D001");
        entity.setDeptName("账套部门");
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
        entity.setPostCode("P001");
        entity.setPostName("账套岗位");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private RoleEntity role(Long accountBookId) {
        RoleEntity entity = new RoleEntity();
        entity.setId(ROLE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setRoleCode("TENANT_ROLE");
        entity.setRoleName("账套角色");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private MenuEntity menu() {
        MenuEntity entity = new MenuEntity();
        entity.setId(ENTITY_ID);
        entity.setMenuCode("SYSTEM_USER");
        entity.setMenuName("用户管理");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserEntity user(Long accountBookId) {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setUsername("tenant_user");
        entity.setRealName("账套用户");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserCreateRequest userCreateRequest() {
        return new UserCreateRequest(
                "tenant_user_2",
                "Password123456",
                "E002",
                "账套用户2",
                null,
                null,
                null,
                DEPT_ID,
                POST_ID,
                null
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
