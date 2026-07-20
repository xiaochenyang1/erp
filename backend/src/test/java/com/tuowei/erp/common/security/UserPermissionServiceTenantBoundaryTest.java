package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class UserPermissionServiceTenantBoundaryTest {

    private static final Long USER_ID = 8101L;
    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long ROLE_ID = 8201L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(UserRoleEntity.class);
        initTableInfo(RoleEntity.class);
    }

    @Test
    void loadPermissionsFiltersActiveRolesByCompanyAndAccountBook() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of());

        UserPermissionService service = new UserPermissionService(
                userRoleMapper,
                roleMapper,
                mock(RoleMenuMapper.class),
                mock(MenuMapper.class),
                CacheService.NOOP
        );

        assertThat(service.loadPermissions(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID)).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    @Test
    void loadPermissionsUsesAccountBookScopedCacheKey() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RecordingCacheService cacheService = new RecordingCacheService();
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(activeRole()));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu()));
        when(menuMapper.selectList(any())).thenReturn(List.of(menu("system:user:view"), menu("system:user:update")));
        UserPermissionService service = new UserPermissionService(
                userRoleMapper,
                roleMapper,
                roleMenuMapper,
                menuMapper,
                cacheService
        );

        Set<String> permissions = service.loadPermissions(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(permissions).containsExactly("system:user:view", "system:user:update");
        assertThat(cacheService.loadedKey).isEqualTo("erp:101:202:permission:user:8101");
        assertThat(cacheService.loadedValue).isEqualTo("system:user:view\nsystem:user:update");
    }

    @Test
    void loadPermissionsRestoresPermissionsFromCacheWithoutQueryingMappers() {
        RecordingCacheService cacheService = new RecordingCacheService();
        cacheService.cachedValue = "system:user:view\nsystem:user:update";
        UserPermissionService service = new UserPermissionService(
                mock(UserRoleMapper.class),
                mock(RoleMapper.class),
                mock(RoleMenuMapper.class),
                mock(MenuMapper.class),
                cacheService
        );

        Set<String> permissions = service.loadPermissions(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(permissions).containsExactly("system:user:view", "system:user:update");
        assertThat(cacheService.loaderCalled).isFalse();
    }

    @Test
    void evictUserPermissionsUsesAccountBookScopedCacheKey() {
        RecordingCacheService cacheService = new RecordingCacheService();
        UserPermissionService service = new UserPermissionService(
                mock(UserRoleMapper.class),
                mock(RoleMapper.class),
                mock(RoleMenuMapper.class),
                mock(MenuMapper.class),
                cacheService
        );

        service.evictUserPermissions(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(cacheService.evictedKey).isEqualTo("erp:101:202:permission:user:8101");
    }

    private static UserRoleEntity userRole() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(ROLE_ID);
        return entity;
    }

    private static RoleEntity activeRole() {
        RoleEntity entity = new RoleEntity();
        entity.setId(ROLE_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setRoleCode("USER_ADMIN");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static RoleMenuEntity roleMenu() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(ROLE_ID);
        entity.setMenuId(9301L);
        return entity;
    }

    private static MenuEntity menu(String permission) {
        MenuEntity entity = new MenuEntity();
        entity.setPermission(permission);
        return entity;
    }

    private static final class RecordingCacheService implements CacheService {

        private String cachedValue;
        private String loadedKey;
        private String loadedValue;
        private String evictedKey;
        private boolean loaderCalled;

        @Override
        public String getOrLoad(String key, java.time.Duration ttl, Supplier<String> loader) {
            loadedKey = key;
            if (cachedValue != null) {
                return cachedValue;
            }
            loaderCalled = true;
            loadedValue = loader.get();
            return loadedValue;
        }

        @Override
        public void evict(String key) {
            evictedKey = key;
        }

        @Override
        public void evictByPrefix(String keyPrefix) {
            evictedKey = keyPrefix;
        }
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
