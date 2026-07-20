package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DataScopeServiceActiveRoleTenantBoundaryTest {

    private static final Long USER_ID = 8301L;
    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long ROLE_ID = 8401L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(UserRoleEntity.class);
        initTableInfo(RoleEntity.class);
    }

    @Test
    void buildSnapshotFiltersActiveRolesByCompanyAndAccountBook() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserDataScopeMapper userDataScopeMapper = mock(UserDataScopeMapper.class);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of());
        when(userDataScopeMapper.selectList(any())).thenReturn(List.of());

        DataScopeService service = new DataScopeService(
                userRoleMapper,
                roleMapper,
                mock(RoleDataScopeMapper.class),
                userDataScopeMapper
        );

        assertThat(service.buildSnapshot(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID)).isEqualTo(DataScopeSnapshot.none());

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    private static UserRoleEntity userRole() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(ROLE_ID);
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
