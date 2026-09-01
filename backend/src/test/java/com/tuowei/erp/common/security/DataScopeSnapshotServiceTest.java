package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DataScopeSnapshotServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long COMPANY_ID = 11L;
    private static final Long ACCOUNT_BOOK_ID = 13L;
    private static final Long ROLE_ID = 17L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(UserRoleEntity.class);
        initTableInfo(RoleEntity.class);
        initTableInfo(RoleDataScopeEntity.class);
        initTableInfo(UserDataScopeEntity.class);
    }

    @Test
    void combinesRoleAndUserScopes() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleDataScopeMapper roleDataScopeMapper = mock(RoleDataScopeMapper.class);
        UserDataScopeMapper userDataScopeMapper = mock(UserDataScopeMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(role("BUYER")));
        when(roleDataScopeMapper.selectList(any())).thenReturn(List.of(
                roleScope("DEPT", null),
                roleScope("WAREHOUSE", 71L),
                roleScope("WAREHOUSE", 71L)
        ));
        when(userDataScopeMapper.selectList(any())).thenReturn(List.of(
                userScope("POST", null),
                userScope("SELF", null),
                userScope("WAREHOUSE", 73L)
        ));

        DataScopeSnapshot snapshot = service(
                userRoleMapper, roleMapper, roleDataScopeMapper, userDataScopeMapper
        ).buildSnapshot(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(snapshot).isEqualTo(new DataScopeSnapshot(
                false, true, true, true, Set.of(71L, 73L)
        ));
        ArgumentCaptor<LambdaQueryWrapper<RoleDataScopeEntity>> roleScopeWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleDataScopeMapper).selectList(roleScopeWrapper.capture());
        assertThat(roleScopeWrapper.getValue().getSqlSegment()).contains("role_id");
        assertThat(roleScopeWrapper.getValue().getParamNameValuePairs()).containsValue(ROLE_ID);
        ArgumentCaptor<LambdaQueryWrapper<UserDataScopeEntity>> userScopeWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userDataScopeMapper).selectList(userScopeWrapper.capture());
        assertThat(userScopeWrapper.getValue().getSqlSegment()).contains("user_id");
        assertThat(userScopeWrapper.getValue().getParamNameValuePairs()).containsValue(USER_ID);
    }

    @Test
    void superAdminShortCircuitsRoleAndUserScopeQueries() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleDataScopeMapper roleDataScopeMapper = mock(RoleDataScopeMapper.class);
        UserDataScopeMapper userDataScopeMapper = mock(UserDataScopeMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(role("SUPER_ADMIN")));

        DataScopeSnapshot snapshot = service(
                userRoleMapper, roleMapper, roleDataScopeMapper, userDataScopeMapper
        ).buildSnapshot(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(snapshot).isEqualTo(DataScopeSnapshot.all());
        verifyNoInteractions(roleDataScopeMapper, userDataScopeMapper);
    }

    @Test
    void userScopesStillApplyWhenNoRolesAreAssigned() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleDataScopeMapper roleDataScopeMapper = mock(RoleDataScopeMapper.class);
        UserDataScopeMapper userDataScopeMapper = mock(UserDataScopeMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(userDataScopeMapper.selectList(any())).thenReturn(List.of(userScope("WAREHOUSE", 79L)));

        DataScopeSnapshot snapshot = service(
                userRoleMapper, roleMapper, roleDataScopeMapper, userDataScopeMapper
        ).buildSnapshot(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(snapshot).isEqualTo(new DataScopeSnapshot(
                false, false, false, false, Set.of(79L)
        ));
        verifyNoInteractions(roleMapper, roleDataScopeMapper);
    }

    @Test
    void explicitAllScopeOverridesNarrowerScopes() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleDataScopeMapper roleDataScopeMapper = mock(RoleDataScopeMapper.class);
        UserDataScopeMapper userDataScopeMapper = mock(UserDataScopeMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(role("BUYER")));
        when(roleDataScopeMapper.selectList(any())).thenReturn(List.of(roleScope("ALL", null)));
        when(userDataScopeMapper.selectList(any())).thenReturn(List.of(userScope("WAREHOUSE", 83L)));

        DataScopeSnapshot snapshot = service(
                userRoleMapper, roleMapper, roleDataScopeMapper, userDataScopeMapper
        ).buildSnapshot(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID);

        assertThat(snapshot).isEqualTo(DataScopeSnapshot.all());
    }

    private DataScopeSnapshotService service(
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            UserDataScopeMapper userDataScopeMapper
    ) {
        return new DataScopeSnapshotService(
                userRoleMapper, roleMapper, roleDataScopeMapper, userDataScopeMapper
        );
    }

    private static UserRoleEntity userRole() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(ROLE_ID);
        return entity;
    }

    private static RoleEntity role(String roleCode) {
        RoleEntity entity = new RoleEntity();
        entity.setId(ROLE_ID);
        entity.setRoleCode(roleCode);
        return entity;
    }

    private static RoleDataScopeEntity roleScope(String scopeType, Long warehouseId) {
        RoleDataScopeEntity entity = new RoleDataScopeEntity();
        entity.setRoleId(ROLE_ID);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        return entity;
    }

    private static UserDataScopeEntity userScope(String scopeType, Long warehouseId) {
        UserDataScopeEntity entity = new UserDataScopeEntity();
        entity.setUserId(USER_ID);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        return entity;
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
